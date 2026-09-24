package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentSubscription;
import br.com.nuvemcustomfields.entity.PaymentSubscriptionStatus;
import br.com.nuvemcustomfields.entity.PlanEvent;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.payment.GatewayCheckout;
import br.com.nuvemcustomfields.payment.GatewayInvoice;
import br.com.nuvemcustomfields.payment.GatewaySubscription;
import br.com.nuvemcustomfields.payment.PaymentGateway;
import br.com.nuvemcustomfields.payment.PaymentGatewayException;
import br.com.nuvemcustomfields.properties.MercadoPagoProperties;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentSubscriptionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentSubscriptionService.class);

    private final StoreRepository stores;
    private final PaymentSubscriptionRepository subscriptions;
    private final PlanEventRepository planEvents;
    private final PaymentGatewayRouter router;
    private final NuvemshopApiClient nuvemshopApi;
    private final NuvemshopProperties nuvemshopProperties;
    private final MercadoPagoProperties mercadoPagoProperties;

    public PaymentSubscriptionService(
            StoreRepository stores,
            PaymentSubscriptionRepository subscriptions,
            PlanEventRepository planEvents,
            PaymentGatewayRouter router,
            NuvemshopApiClient nuvemshopApi,
            NuvemshopProperties nuvemshopProperties,
            MercadoPagoProperties mercadoPagoProperties
    ) {
        this.stores = stores;
        this.subscriptions = subscriptions;
        this.planEvents = planEvents;
        this.router = router;
        this.nuvemshopApi = nuvemshopApi;
        this.nuvemshopProperties = nuvemshopProperties;
        this.mercadoPagoProperties = mercadoPagoProperties;
    }

    public boolean available(Store store) {
        if (router.forStore(store).isEmpty()
                && store.getStoreCountryCode() != null && store.getStoreCurrency() != null) return false;
        refreshProfileIfNeeded(store);
        return router.forStore(store).isPresent();
    }

    public boolean mercadoPagoEnabled() {
        return router.configured(PaymentProviderType.MERCADO_PAGO);
    }

    public BigDecimal amount(Store store, PlanType plan) {
        return router.forStore(store).map(gateway -> gateway.amount(plan)).orElse(BigDecimal.ZERO);
    }

    public String currency(Store store) {
        return router.forStore(store).isPresent() ? "BRL" : store.getStoreCurrency();
    }

    public Optional<PaymentSubscription> find(Long storeId) {
        return subscriptions.findByStoreId(storeId);
    }

    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public String startCheckout(Long storeId, PlanType plan, String payerEmail) {
        if (plan == null || !plan.isBillable()) {
            throw new IllegalArgumentException("Selecione o plano Essencial ou Pro.");
        }
        String normalizedPayerEmail = normalizePayerEmail(payerEmail);
        Store store = stores.findActiveByStoreIdForUpdate(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Loja ativa nao encontrada."));
        refreshProfileIfNeeded(store);
        if (store.isCourtesyPremium()) {
            throw new IllegalArgumentException("A cortesia ativa precisa terminar antes da assinatura paga.");
        }
        PaymentGateway gateway = router.requireForStore(store);
        PaymentSubscription subscription = subscriptions.findByStoreId(storeId).orElse(null);
        if (subscription != null && subscription.isAccessActive()) {
            throw new IllegalArgumentException("A loja ja possui uma assinatura ativa.");
        }
        if (subscription != null && subscription.getStatus() == PaymentSubscriptionStatus.PENDING
                && plan == subscription.getPlan()
                && Objects.equals(normalizedPayerEmail, subscription.getPayerEmail())
                && hasText(subscription.getCheckoutUrl())) {
            return subscription.getCheckoutUrl();
        }
        if (subscription != null && subscription.getStatus() == PaymentSubscriptionStatus.PENDING
                && subscription.getProviderSubscriptionId() != null) {
            router.require(subscription.getProvider()).cancel(subscription.getProviderSubscriptionId());
            subscription.setProviderSubscriptionId(null);
        }

        if (subscription == null) {
            subscription = new PaymentSubscription();
            subscription.setStoreId(storeId);
        }
        String reference = "ncf_" + storeId + "_" + UUID.randomUUID().toString().replace("-", "");
        subscription.setProvider(gateway.provider());
        subscription.setPayerEmail(normalizedPayerEmail);
        subscription.setExternalReference(reference);
        subscription.setPlan(plan);
        subscription.setCurrency("BRL");
        subscription.setAmountValue(gateway.amount(plan));
        subscription.setStatus(PaymentSubscriptionStatus.PENDING);
        subscription.setProviderStatus("pending");
        subscription.setCheckoutUrl(null);
        subscription.setLastError(null);
        subscriptions.saveAndFlush(subscription);

        try {
            String returnUrl = nuvemshopProperties.appBaseUrl() + "/admin/billing/return";
            GatewayCheckout checkout = gateway.createCheckout(store, plan, reference, returnUrl, normalizedPayerEmail);
            subscription.setProviderSubscriptionId(checkout.subscriptionId());
            subscription.setCheckoutUrl(checkout.checkoutUrl());
            subscription.setProviderStatus(checkout.providerStatus());
            subscription.setLastSyncedAt(Instant.now());
            subscriptions.save(subscription);
            LOGGER.info("payments.checkout.created store_id={} provider={} plan={} subscription_id={}",
                    storeId, gateway.provider(), plan, checkout.subscriptionId());
            return checkout.checkoutUrl();
        } catch (RuntimeException ex) {
            subscription.setStatus(PaymentSubscriptionStatus.ERROR);
            subscription.setLastError(truncate(ex.getMessage()));
            subscription.setLastSyncedAt(Instant.now());
            subscriptions.save(subscription);
            throw ex;
        }
    }

    @Transactional
    public PaymentSubscription reconcile(Long storeId) {
        PaymentSubscription local = subscriptions.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Assinatura local nao encontrada."));
        if (local.getProviderSubscriptionId() == null) {
            return local;
        }
        PaymentGateway gateway = router.require(local.getProvider());
        if (local.isCancellationPending()) {
            gateway.cancel(local.getProviderSubscriptionId());
            local.setCancellationPending(false);
            subscriptions.saveAndFlush(local);
        }
        GatewaySubscription remote = gateway.getSubscription(local.getProviderSubscriptionId());
        Optional<GatewayInvoice> invoice = gateway.getLatestInvoice(remote.id());
        return synchronize(local, remote, invoice.orElse(null), "PAYMENT_RECONCILE");
    }

    @Transactional
    public PaymentSubscription synchronizeFromSubscription(PaymentProviderType provider, String subscriptionId) {
        PaymentGateway gateway = router.require(provider);
        GatewaySubscription remote = gateway.getSubscription(subscriptionId);
        PaymentSubscription local = locate(remote);
        return synchronize(local, remote, gateway.getLatestInvoice(subscriptionId).orElse(null), "PAYMENT_WEBHOOK");
    }

    @Transactional
    public PaymentSubscription synchronizeFromInvoice(PaymentProviderType provider, String invoiceId) {
        PaymentGateway gateway = router.require(provider);
        GatewayInvoice invoice = gateway.getInvoice(invoiceId);
        GatewaySubscription remote = gateway.getSubscription(invoice.subscriptionId());
        PaymentSubscription local = locate(remote);
        return synchronize(local, remote, invoice, "PAYMENT_WEBHOOK");
    }

    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public void cancel(Long storeId) {
        PaymentSubscription local = subscriptions.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Assinatura nao encontrada."));
        local.setCancellationPending(true);
        subscriptions.saveAndFlush(local);
        try {
            router.require(local.getProvider()).cancel(local.getProviderSubscriptionId());
            local.setCancellationPending(false);
            subscriptions.saveAndFlush(local);
            reconcile(storeId);
        } catch (RuntimeException ex) {
            local.setLastError(truncate(ex.getMessage()));
            subscriptions.save(local);
            throw ex;
        }
    }

    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public void cancelAfterUninstall(Long storeId) {
        subscriptions.findByStoreId(storeId).ifPresent(local -> {
            deactivate(local, stores.findByStoreId(storeId).orElse(null), "APP_UNINSTALLED");
            if (local.getProviderSubscriptionId() == null || local.getStatus() == PaymentSubscriptionStatus.CANCELED) return;
            local.setCancellationPending(true);
            try {
                router.require(local.getProvider()).cancel(local.getProviderSubscriptionId());
                local.setCancellationPending(false);
                local.setStatus(PaymentSubscriptionStatus.CANCELED);
                local.setProviderStatus("cancelled");
                local.setLastError(null);
            } catch (RuntimeException ex) {
                local.setLastError(truncate(ex.getMessage()));
                LOGGER.error("payments.uninstall.cancel_failed store_id={} message={}", storeId, ex.getMessage());
            }
            subscriptions.save(local);
        });
    }

    private PaymentSubscription synchronize(
            PaymentSubscription local,
            GatewaySubscription remote,
            GatewayInvoice invoice,
            String source
    ) {
        validateRemote(local, remote);
        local.setProviderSubscriptionId(remote.id());
        local.setProviderStatus(remote.status());
        if (remote.nextPaymentAt() != null) local.setNextPaymentAt(remote.nextPaymentAt());
        local.setLastSyncedAt(Instant.now());
        local.setLastError(null);
        if (invoice != null) {
            local.setLastPaymentId(invoice.paymentId());
            local.setLastPaymentStatus(invoice.paymentStatus());
        }
        Store store = stores.findByStoreId(local.getStoreId())
                .orElseThrow(() -> new IllegalArgumentException("Loja da assinatura nao encontrada."));
        String status = remote.status() == null ? "" : remote.status().toLowerCase();
        if ("authorized".equals(status) && invoice != null && invoice.approved()) {
            local.setStatus(PaymentSubscriptionStatus.ACTIVE);
            local.setGraceUntil(null);
            local.setCancellationPending(false);
            activate(local, store, source);
        } else if ("paused".equals(status)) {
            local.setStatus(PaymentSubscriptionStatus.PAUSED);
            deactivate(local, store, source);
        } else if ("cancelled".equals(status) || "canceled".equals(status)) {
            local.setStatus(PaymentSubscriptionStatus.CANCELED);
            local.setCancellationPending(false);
            if (local.getNextPaymentAt() == null || !Instant.now().isBefore(local.getNextPaymentAt())) {
                deactivate(local, store, source);
            }
        } else if (local.isAccessActive() && invoice != null && !invoice.approved()) {
            local.setStatus(PaymentSubscriptionStatus.PAST_DUE);
            if (local.getGraceUntil() == null) {
                local.setGraceUntil(Instant.now().plus(mercadoPagoProperties.safeGraceDays(), ChronoUnit.DAYS));
            }
            if (!Instant.now().isBefore(local.getGraceUntil())) deactivate(local, store, source);
        } else {
            local.setStatus(PaymentSubscriptionStatus.PENDING);
        }
        stores.save(store);
        return subscriptions.save(local);
    }

    private PaymentSubscription locate(GatewaySubscription remote) {
        return subscriptions.findByProviderSubscriptionId(remote.id())
                .or(() -> subscriptions.findByExternalReference(remote.externalReference()))
                .orElseThrow(() -> new IllegalArgumentException("Assinatura notificada nao pertence a uma loja."));
    }

    private void validateRemote(PaymentSubscription local, GatewaySubscription remote) {
        if (!local.getExternalReference().equals(remote.externalReference())) {
            throw new IllegalArgumentException("Referencia externa da assinatura divergente.");
        }
        if (!local.getCurrency().equalsIgnoreCase(remote.currency())
                || local.getAmountValue().compareTo(remote.amount()) != 0) {
            throw new IllegalArgumentException("Valor ou moeda da assinatura divergente.");
        }
    }

    private void activate(PaymentSubscription local, Store store, String source) {
        PlanType previous = store.getPlan();
        local.setAccessActive(true);
        store.setBillingSuspended(false);
        if (previous != local.getPlan()) {
            store.setPlan(local.getPlan());
            event(store.getStoreId(), previous, local.getPlan(), source);
        }
    }

    private void deactivate(PaymentSubscription local, Store store, String source) {
        local.setAccessActive(false);
        if (store != null && store.getPlan().isBillable()) {
            PlanType previous = store.getPlan();
            store.setPlan(PlanType.FREE);
            store.setBillingSuspended(false);
            stores.save(store);
            event(store.getStoreId(), previous, PlanType.FREE, source);
        }
    }

    private void event(Long storeId, PlanType from, PlanType to, String source) {
        PlanEvent event = new PlanEvent();
        event.setStoreId(storeId);
        event.setFromPlan(from);
        event.setToPlan(to);
        event.setSource(source);
        planEvents.save(event);
    }

    private void refreshProfileIfNeeded(Store store) {
        if (store.getStoreCountryCode() != null && store.getStoreCurrency() != null && store.getStoreEmail() != null) return;
        try {
            var profile = nuvemshopApi.getStoreProfile(store);
            store.setStoreName(profile.name());
            store.setStoreCountryCode(profile.countryCode());
            store.setStoreCurrency(profile.currency());
            store.setStoreEmail(profile.email());
            stores.save(store);
        } catch (RuntimeException ex) {
            LOGGER.warn("payments.store_profile.refresh_failed store_id={} message={}", store.getStoreId(), ex.getMessage());
        }
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
    private static String normalizePayerEmail(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf('@');
        if (separator <= 0 || separator == normalized.length() - 1 || normalized.indexOf('@', separator + 1) >= 0
                || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Informe um e-mail valido da conta Mercado Pago.");
        }
        return normalized;
    }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
