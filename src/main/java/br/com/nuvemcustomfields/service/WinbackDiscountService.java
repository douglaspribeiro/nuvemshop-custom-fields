package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.properties.WinbackDiscountProperties;
import br.com.nuvemcustomfields.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class WinbackDiscountService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WinbackDiscountService.class);
    private final WinbackCouponRepository coupons;
    private final WinbackCampaignRepository campaigns;
    private final PaymentSubscriptionRepository subscriptions;
    private final PaymentNotificationOutboxRepository history;
    private final StoreRepository stores;
    private final EfiGateway efi;
    private final PaymentGatewayRouter router;
    private final WinbackDiscountProperties properties;
    private final PlanEventRepository planEvents;
    public WinbackDiscountService(WinbackCouponRepository coupons, WinbackCampaignRepository campaigns,
            PaymentSubscriptionRepository subscriptions, PaymentNotificationOutboxRepository history,
            StoreRepository stores, EfiGateway efi, PaymentGatewayRouter router, WinbackDiscountProperties properties,
            PlanEventRepository planEvents) {
        this.coupons = coupons; this.campaigns = campaigns; this.subscriptions = subscriptions;
        this.history = history; this.stores = stores; this.efi = efi; this.router = router; this.properties = properties;
        this.planEvents = planEvents;
    }

    public boolean available(Store store) {
        return !store.isErasurePending() && properties.enabled() && (efi.sandbox() || properties.allowProduction())
                && efi.configured() && efi.supports(store)
                && router.forStore(store).map(g -> g.provider() == PaymentProviderType.EFI).orElse(false);
    }

    public boolean hasPaidHistory(Long storeId) {
        return history.existsByStoreId(storeId) || subscriptions.findByStoreId(storeId)
                .map(s -> new GatewayInvoice("", "", "", s.getLastPaymentStatus()).approved()).orElse(false)
                || planEvents.existsByStoreIdAndToPlanInAndSourceIn(storeId,
                    java.util.List.of(PlanType.PREMIUM, PlanType.PREMIUM_PLUS, PlanType.PREMIUM_ULTRA),
                    java.util.List.of("EFI_CHECKOUT", "PAYMENT_WEBHOOK", "PAYMENT_RECONCILE", "PENDING_TIMEOUT_RECONCILE"));
    }

    /** Called under the store lock; one coupon for the entire retained store history. */
    @Transactional
    public Optional<WinbackCoupon> issue(WinbackCampaign campaign, Store store) {
        if (!available(store) || campaign.isPaidBeforeDeparture() || campaign.getRespondedAt() == null
                || campaign.getOptedOutAt() != null || campaigns.existsByStoreIdAndOptedOutAtIsNotNull(store.getStoreId())
                || hasPaidHistory(store.getStoreId())) return Optional.empty();
        var previous = coupons.findByStoreId(store.getStoreId());
        if (previous.isPresent()) return previous.filter(c -> c.getCampaignId().equals(campaign.getId())
                && c.getUsedAt() == null && c.getExpiresAt().isAfter(Instant.now()));
        return Optional.of(coupons.saveAndFlush(new WinbackCoupon(store.getStoreId(), campaign.getId())));
    }

    @Transactional(readOnly = true)
    public Quote quote(Store store, PlanType plan) {
        BigDecimal regular = efi.amount(plan);
        var coupon = coupons.findByStoreId(store.getStoreId()).filter(c -> eligible(c, store));
        return coupon.map(c -> new Quote(c.getCode(), half(regular), regular))
                .orElseGet(() -> new Quote(null, regular, regular));
    }

    private boolean eligible(WinbackCoupon coupon, Store store) {
        if (!available(store) || !store.isActive() || coupon.getUsedAt() != null
                || coupon.getExpiresAt().isBefore(Instant.now()) || hasPaidHistory(store.getStoreId())) return false;
        var c = campaigns.findById(coupon.getCampaignId()).orElse(null);
        return c != null && c.getReinstalledAt() != null && !c.isPaidBeforeDeparture()
                && c.getOptedOutAt() == null && !campaigns.existsByStoreIdAndOptedOutAtIsNotNull(store.getStoreId());
    }

    @Transactional
    public Quote reserve(Store store, PlanType plan, String code, String reference) {
        BigDecimal regular = efi.amount(plan);
        if (code == null || code.isBlank()) return new Quote(null, regular, regular);
        var coupon = coupons.findById(code.strip().toUpperCase()).orElseThrow(() -> new IllegalArgumentException("Cupom inválido."));
        if (!store.getStoreId().equals(coupon.getStoreId()) || !eligible(coupon, store))
            throw new IllegalArgumentException("O cupom não está disponível para esta loja. Confira o prazo e a reinstalação.");
        BigDecimal first = half(regular);
        coupon.reserve(reference, regular, first);
        return new Quote(coupon.getCode(), first, regular);
    }

    public void bindSubscription(PaymentSubscription local) {
        couponFor(local).ifPresent(c -> c.subscription(local.getProviderSubscriptionId()));
    }
    public void firstPayment(PaymentSubscription local, String paymentId) {
        if (local.getWinbackCouponCode() == null || paymentId == null) return;
        local.setWinbackFirstPaymentId(paymentId);
        couponFor(local).ifPresent(c -> c.firstPayment(paymentId));
    }
    public void confirm(PaymentSubscription local, GatewayInvoice invoice) {
        if (invoice == null || !invoice.approved() || local.getWinbackFirstPaymentId() == null
                || !local.getWinbackFirstPaymentId().equals(invoice.paymentId())
                || !local.getProviderSubscriptionId().equals(invoice.subscriptionId())) return;
        couponFor(local).filter(c -> local.getProviderSubscriptionId().equals(c.getSubscriptionId())).ifPresent(c -> {
            c.used();
            campaigns.findById(c.getCampaignId()).ifPresent(WinbackCampaign::converted);
        });
    }
    private Optional<WinbackCoupon> couponFor(PaymentSubscription local) {
        if (local.getWinbackCouponCode() == null) return Optional.empty();
        return coupons.findById(local.getWinbackCouponCode()).filter(c ->
                local.getStoreId().equals(c.getStoreId()) && local.getExternalReference().equals(c.getExternalReference()));
    }

    /** Restore after confirmation of the first approved charge; pending charges keep their original price. */
    public void restore(PaymentSubscription local) {
        if (!local.isWinbackRestorePending() || local.getProviderSubscriptionId() == null) return;
        if (local.getProviderEnvironment() != efi.environment())
            throw new PaymentGatewayException("A restauração precisa das credenciais do ambiente original.");
        GatewayInvoice invoice = null;
        if (local.getWinbackFirstPaymentId() == null) {
            var first = efi.getFirstInvoice(local.getProviderSubscriptionId());
            if (first.isEmpty()) return;
            firstPayment(local, first.get().paymentId());
            confirm(local, first.get());
            invoice = first.get();
        }
        var coupon = couponFor(local).orElseThrow(() -> new IllegalStateException("Vínculo do cupom ausente."));
        if (coupon.getUsedAt() == null) {
            if (invoice == null) invoice = efi.getCharge(local.getProviderSubscriptionId(), local.getWinbackFirstPaymentId());
            if (invoice == null) throw new PaymentGatewayException("Confirmação da primeira cobrança indisponível.");
            confirm(local, invoice);
            if (!invoice.approved()) {
                if (java.util.Set.of("unpaid", "canceled", "cancelled", "refunded").contains(
                        invoice.paymentStatus() == null ? "" : invoice.paymentStatus().toLowerCase(java.util.Locale.ROOT))) {
                    var remote = efi.getSubscription(local.getProviderSubscriptionId());
                    if (!java.util.Set.of("canceled", "cancelled", "expired").contains(remote.status())) {
                        efi.cancel(local.getProviderSubscriptionId());
                        remote = efi.getSubscription(local.getProviderSubscriptionId());
                    }
                    if (!java.util.Set.of("canceled", "cancelled", "expired").contains(remote.status()))
                        throw new PaymentGatewayException("Cancelamento da recorrência promocional ainda não confirmado.");
                    local.winbackRecurrenceStopped(); coupon.stopped();
                }
                return;
            }
        }
        if (coupon.getUsedAt() == null) throw new PaymentGatewayException("Primeira cobrança ainda não confirmada.");
        String knownStatus = local.getProviderStatus() == null ? "" : local.getProviderStatus().toLowerCase(java.util.Locale.ROOT);
        if (java.util.Set.of("canceled", "cancelled", "expired").contains(knownStatus)) {
            var remote = efi.getSubscription(local.getProviderSubscriptionId());
            if (java.util.Set.of("canceled", "cancelled", "expired").contains(remote.status())) {
                local.winbackRecurrenceStopped(); coupon.stopped(); return;
            }
        }
        efi.updateRecurringAmount(local.getProviderSubscriptionId(), local.getPlan(), local.getWinbackRegularAmount());
        local.winbackRestored();
        couponFor(local).ifPresent(WinbackCoupon::restored);
    }

    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public void retryRestore(Long storeId) {
        if (stores.findByStoreIdForUpdate(storeId).isEmpty()) return;
        subscriptions.findByStoreId(storeId).filter(PaymentSubscription::isWinbackRestorePending).ifPresent(s -> {
            restore(s);
            subscriptions.saveAndFlush(s);
        });
    }

    /** Remove recurring underpricing before its local recovery data are erased. */
    public void beforeErasure(Long storeId) {
        subscriptions.findByStoreId(storeId).filter(PaymentSubscription::isWinbackRestorePending).ifPresent(s -> {
            if (s.getProviderSubscriptionId() == null) return;
            try {
                restore(s);
                if (!s.isWinbackRestorePending()) return;
            } catch (RuntimeException ex) {
                LOGGER.warn("winback.discount.erasure_restore_failed type={}", ex.getClass().getSimpleName());
            }
            // No restoration mapping can survive redact. Stop future reduced renewals instead.
            try {
                if (s.getProviderEnvironment() != efi.environment()) throw new IllegalStateException("Ambiente divergente.");
                efi.cancel(s.getProviderSubscriptionId());
            } catch (RuntimeException ex) {
                LOGGER.error("winback.discount.erasure_remote_cleanup_failed type={}", ex.getClass().getSimpleName());
            }
        });
        // JDBC erasure follows this method: flush managed financial changes before deleting their rows.
        subscriptions.flush();
    }

    private static BigDecimal half(BigDecimal regular) {
        regular = regular.setScale(2, RoundingMode.UNNECESSARY);
        // Round the discount up by one cent when needed, so the customer gets at least 50% off.
        BigDecimal first = regular.divide(BigDecimal.valueOf(2), 2, RoundingMode.DOWN);
        if (first.signum() <= 0) throw new IllegalArgumentException("Preço incompatível com o desconto.");
        return first;
    }
    public record Quote(String code, BigDecimal firstAmount, BigDecimal regularAmount) {
        public boolean discounted() { return code != null; }
    }
}
