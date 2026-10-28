package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentEnvironment;
import br.com.nuvemcustomfields.entity.PaymentAttempt;
import br.com.nuvemcustomfields.entity.PaymentAttemptStatus;
import br.com.nuvemcustomfields.entity.PaymentSubscription;
import br.com.nuvemcustomfields.entity.PaymentSubscriptionStatus;
import br.com.nuvemcustomfields.entity.PlanEvent;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.payment.GatewayCheckout;
import br.com.nuvemcustomfields.payment.GatewayInvoice;
import br.com.nuvemcustomfields.payment.GatewaySubscription;
import br.com.nuvemcustomfields.payment.EfiGateway;
import br.com.nuvemcustomfields.payment.PaymentGateway;
import br.com.nuvemcustomfields.payment.PaymentGatewayException;
import br.com.nuvemcustomfields.properties.MercadoPagoProperties;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.PaymentAttemptRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentSubscriptionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentSubscriptionService.class);
    private static final long PENDING_TIMEOUT_MINUTES = 30;
    private static final String EXPIRED_ATTEMPT_MESSAGE =
            "A tentativa anterior não foi confirmada em 30 minutos e foi cancelada. Você pode tentar novamente.";

    private final StoreRepository stores;
    private final PaymentSubscriptionRepository subscriptions;
    private final PlanEventRepository planEvents;
    private final PaymentGatewayRouter router;
    private final NuvemshopApiClient nuvemshopApi;
    private final NuvemshopProperties nuvemshopProperties;
    private final MercadoPagoProperties mercadoPagoProperties;
    private final EfiGateway efi;
    private final PaymentNotificationService paymentNotifications;
    private final PaymentAttemptRepository attempts;
    private final WinbackDiscountService winbackDiscounts;
    private PaymentCatalogPriceRepository catalog;
    @Autowired
    public void setPaymentCatalog(PaymentCatalogPriceRepository catalog) { this.catalog = catalog; }
    private DodoLiveCheckoutService dodoLiveCheckout;
    @Autowired(required = false)
    public void setDodoLiveCheckout(DodoLiveCheckoutService service) { this.dodoLiveCheckout = service; }
    private DodoTestCheckoutService dodoTestCheckout;
    @Autowired(required = false)
    public void setDodoTestCheckout(DodoTestCheckoutService service) { this.dodoTestCheckout = service; }
    private CreemCheckoutService creemCheckout;
    private org.springframework.beans.factory.ObjectProvider<DlocalGoUpgradeService> dlocalGoUpgrades;
    @Autowired
    public void setDlocalGoUpgrades(org.springframework.beans.factory.ObjectProvider<DlocalGoUpgradeService> value) { dlocalGoUpgrades=value; }
    private DlocalGoCheckoutService dlocalGoCheckout;
    @org.springframework.beans.factory.annotation.Autowired
    public void setDlocalGoCheckout(DlocalGoCheckoutService service) { this.dlocalGoCheckout = service; }

    private StripeCheckoutService stripeCheckout;
    @Autowired(required = false)
    public void setStripeCheckout(StripeCheckoutService service) { this.stripeCheckout = service; }
    private CreemUpgradeService creemUpgrade;
    @Autowired(required = false)
    public void setCreemUpgrade(CreemUpgradeService service) { this.creemUpgrade = service; }

    @Autowired(required = false)
    public void setCreemCheckout(CreemCheckoutService service) { this.creemCheckout = service; }

    @Autowired
    public PaymentSubscriptionService(
            StoreRepository stores,
            PaymentSubscriptionRepository subscriptions,
            PlanEventRepository planEvents,
            PaymentGatewayRouter router,
            NuvemshopApiClient nuvemshopApi,
            NuvemshopProperties nuvemshopProperties,
            MercadoPagoProperties mercadoPagoProperties,
            EfiGateway efi,
            PaymentNotificationService paymentNotifications,
            PaymentAttemptRepository attempts,
            WinbackDiscountService winbackDiscounts
    ) {
        this.stores = stores;
        this.subscriptions = subscriptions;
        this.planEvents = planEvents;
        this.router = router;
        this.nuvemshopApi = nuvemshopApi;
        this.nuvemshopProperties = nuvemshopProperties;
        this.mercadoPagoProperties = mercadoPagoProperties;
        this.efi = efi;
        this.paymentNotifications = paymentNotifications;
        this.attempts = attempts;
        this.winbackDiscounts = winbackDiscounts;
    }

    public PaymentSubscriptionService(StoreRepository stores, PaymentSubscriptionRepository subscriptions,
            PlanEventRepository planEvents, PaymentGatewayRouter router, NuvemshopApiClient nuvemshopApi,
            NuvemshopProperties nuvemshopProperties, MercadoPagoProperties mercadoPagoProperties, EfiGateway efi,
            PaymentNotificationService paymentNotifications, PaymentAttemptRepository attempts) {
        this(stores, subscriptions, planEvents, router, nuvemshopApi, nuvemshopProperties, mercadoPagoProperties,
                efi, paymentNotifications, attempts, null);
    }

    public PaymentSubscriptionService(StoreRepository stores, PaymentSubscriptionRepository subscriptions,
            PlanEventRepository planEvents, PaymentGatewayRouter router, NuvemshopApiClient nuvemshopApi,
            NuvemshopProperties nuvemshopProperties, MercadoPagoProperties mercadoPagoProperties,
            EfiGateway efi, PaymentNotificationService paymentNotifications) {
        this(stores, subscriptions, planEvents, router, nuvemshopApi, nuvemshopProperties,
                mercadoPagoProperties, efi, paymentNotifications, null, null);
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

    public boolean anyGatewayEnabled() { return efiEnabled() || mercadoPagoEnabled() || router.configured(PaymentProviderType.CREEM) || router.configured(PaymentProviderType.DODO_TEST) || router.configured(PaymentProviderType.DODO) || router.configured(PaymentProviderType.STRIPE) || router.configured(PaymentProviderType.DLOCAL_GO); }
    public Optional<PaymentProviderType> provider(Store store) {
        return router.forStore(store).map(PaymentGateway::provider);
    }

    public boolean efiEnabled() { return efi.configured(); }
    public String efiPayeeCode() { return efi.payeeCode(); }
    public boolean efiSandbox() { return efi.sandbox(); }

    public String customerFacingError(String technicalDetail) {
        if (technicalDetail != null && technicalDetail.contains("transaction_checkout_not_enabled")) {
            return "Os pagamentos estão sendo ativados para esta loja. Tente novamente em alguns minutos ou contate o suporte.";
        }
        if (technicalDetail != null && technicalDetail.contains("transaction_default_checkout_url_not_set")) {
            return "O checkout está sendo configurado. Tente novamente em alguns minutos ou contate o suporte.";
        }
        return "Não foi possível iniciar o pagamento agora. Tente novamente ou contate o suporte.";
    }

    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public void payWithEfi(Long storeId, PlanType plan, EfiGateway.EfiPayer payer, String paymentToken) {
        payWithEfi(storeId, plan, payer, paymentToken, null);
    }

    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public void payWithEfi(Long storeId, PlanType plan, EfiGateway.EfiPayer payer, String paymentToken, String couponCode) {
        if (plan == null || !plan.isBillable()) throw new IllegalArgumentException("Selecione um plano pago.");
        if (payer == null || !validPayer(payer) || paymentToken == null || !paymentToken.matches("[a-zA-Z0-9]{20,120}")) {
            throw new IllegalArgumentException("Confira os dados do pagador e do cartão.");
        }
        Store store = stores.findActiveByStoreIdForUpdate(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Loja ativa não encontrada."));
        refreshProfileIfNeeded(store);
        if (!efi.configured() || !efi.supports(store)) throw new IllegalArgumentException("Pagamento Efí indisponível para esta loja.");
        if (plan == PlanType.PREMIUM_ULTRA && !efi.planAvailable(store, plan))
            throw new IllegalArgumentException("O Ultra ainda não está disponível para assinatura.");
        PaymentSubscription local = subscriptions.findByStoreId(storeId).orElse(null);
        if (local != null && (local.getProvider().isStripeOrCreem() || local.getProvider().isDodo() || local.getProvider() == PaymentProviderType.DLOCAL_GO)
                && local.getStatus() == PaymentSubscriptionStatus.PENDING && !hasText(local.getProviderCheckoutId()))
            throw new IllegalStateException("A tentativa anterior no gateway ainda precisa ser conciliada. Contate o suporte.");
        if (local != null && local.getProvider() == PaymentProviderType.EFI
                && local.getStatus() == PaymentSubscriptionStatus.PENDING) {
            if (pendingExpired(local, Instant.now())) {
                expirePendingEfi(storeId);
            } else if (hasText(local.getProviderSubscriptionId())) {
                reconcile(storeId);
            }
            local = subscriptions.findByStoreId(storeId).orElse(local);
            if (local.getStatus() == PaymentSubscriptionStatus.PENDING) {
                throw new IllegalStateException("Aguarde a confirmação do pagamento anterior antes de tentar novamente.");
            }
        }
        if (local != null && local.getStatus() == PaymentSubscriptionStatus.PENDING
                && local.getProvider() != PaymentProviderType.EFI
                && hasText(local.getProviderSubscriptionId())) {
            if (!router.configured(local.getProvider())) {
                throw new IllegalStateException("Existe uma assinatura pendente no provedor anterior. Contate o suporte antes de tentar outro pagamento.");
            }
            local = reconcile(storeId);
        }
        if (local != null && local.isWinbackRestorePending()) {
            restoreWinbackAmount(local);
            if (local.isWinbackRestorePending())
                throw new PaymentGatewayException("A assinatura anterior precisa ser conciliada antes de uma nova cobrança.");
        }
        if (local != null && local.isAccessActive()) throw new IllegalArgumentException("A loja já possui uma assinatura ativa.");
        if (local != null && local.getStatus() == PaymentSubscriptionStatus.PENDING
                && local.getProvider() != PaymentProviderType.EFI
                && hasText(local.getProviderSubscriptionId())) {
            router.require(local.getProvider()).cancel(local.getProviderSubscriptionId());
        } else if (local != null && local.getStatus() == PaymentSubscriptionStatus.ERROR
                && local.getProvider() == PaymentProviderType.EFI
                && hasText(local.getProviderSubscriptionId())) {
            // Uma cobrança recusada pode deixar a assinatura remota ativa; não a abandone
            // antes de criar outra para a mesma loja.
            efi.cancel(local.getProviderSubscriptionId());
        } else if (local != null && local.getStatus() == PaymentSubscriptionStatus.PENDING
                && hasText(local.getProviderCheckoutId()) && local.getProvider() != PaymentProviderType.EFI) {
            if (router.configured(local.getProvider())) {
                router.require(local.getProvider()).cancelCheckout(local.getProviderCheckoutId());
            } else if (local.getProvider() == PaymentProviderType.MERCADO_PAGO) {
                // Sem subscription_id local não há assinatura conhecida para cancelar. Sem
                // as credenciais antigas, substituímos só o checkout local e preservamos o
                // identificador do plano remoto no log para conferência e auditoria.
                LOGGER.warn("payments.checkout.orphaned store_id={} provider={} checkout_id={}",
                        storeId, local.getProvider(), local.getProviderCheckoutId());
            } else {
                throw new IllegalStateException("Checkout pendente em provedor indisponível. Contate o suporte.");
            }
        }
        if (local == null) {
            local = new PaymentSubscription();
            local.setStoreId(storeId);
        }
        String reference = "ncf_" + storeId + "_" + UUID.randomUUID().toString().replace("-", "");
        var quote = winbackDiscounts == null
                ? new WinbackDiscountService.Quote(null, efi.amount(plan), efi.amount(plan))
                : winbackDiscounts.reserve(store, plan, couponCode, reference);
        if (winbackDiscounts == null && hasText(couponCode)) throw new IllegalArgumentException("Cupom indisponível.");
        local.applyWinbackCoupon(quote.code(), quote.discounted() ? quote.regularAmount() : null,
                quote.discounted() ? quote.firstAmount() : null);
        local.setProvider(PaymentProviderType.EFI);
        local.setProviderEnvironment(efi.environment());
        local.setProviderSubscriptionId(null);
        local.setProviderCheckoutId(null);
        local.setProviderPriceId(efi.planId(plan));
        local.setExternalReference(reference);
        local.captureAnalytics();
        local.setAnalyticsFirstPaymentId(null);
        local.setPayerEmail(payer.email());
        local.setPlan(plan);
        local.setCurrency("BRL");
        local.setAmountValue(quote.firstAmount());
        local.setStatus(PaymentSubscriptionStatus.PENDING);
        local.setPendingStartedAt(Instant.now());
        local.setProviderStatus("new");
        local.setCheckoutUrl(null);
        local.setLastPaymentId(null);
        local.setLastPaymentStatus(null);
        local.setLastError(null);
        subscriptions.saveAndFlush(local);
        try {
            String notificationUrl = nuvemshopProperties.appBaseUrl() + "/prod/webhooks/efi3";
            String id = quote.discounted() ? efi.createSubscription(plan, reference, notificationUrl, quote.firstAmount())
                    : efi.createSubscription(plan, reference, notificationUrl);
            local.setProviderSubscriptionId(id);
            // The plan is shared across stores; checkout IDs have a unique database constraint.
            local.setProviderPriceId(efi.planId(plan));
            if (winbackDiscounts != null) winbackDiscounts.bindSubscription(local);
            subscriptions.saveAndFlush(local);
            var paid = efi.pay(id, payer, paymentToken);
            var charge = paid == null ? null : paid.path("charge");
            String chargeId = paid == null ? null : paid.path("charge_id").asText(null);
            if (!hasText(chargeId) && charge != null) chargeId = charge.path("id").asText(null);
            String chargeStatus = paid == null ? null : paid.path("status").asText(null);
            if (!hasText(chargeStatus) && charge != null) chargeStatus = charge.path("status").asText(null);
            if (hasText(chargeId)) {
                local.setLastPaymentId(chargeId);
                local.setLastPaymentStatus(chargeStatus);
                if (winbackDiscounts != null) winbackDiscounts.firstPayment(local, chargeId);
                subscriptions.saveAndFlush(local);
            }
            GatewayInvoice invoice = hasText(chargeId) && hasText(chargeStatus)
                    ? new GatewayInvoice(chargeId, id, chargeId, chargeStatus) : null;
            if (winbackDiscounts != null) {
                winbackDiscounts.confirm(local, invoice);
                restoreWinbackAmount(local);
            }
            GatewaySubscription remote;
            try {
                remote = efi.getSubscription(id);
            } catch (RuntimeException ex) {
                // O POST de pagamento já foi aceito. Uma falha na consulta posterior
                // não significa que o cartão falhou; o webhook/GET de cobrança conciliará.
                LOGGER.warn("payments.efi.checkout_reconcile_deferred store_id={} subscription_id={} type={}",
                        storeId, id, ex.getClass().getSimpleName());
                return;
            }
            try {
                invoice = efi.getLatestInvoice(id).orElse(invoice);
            } catch (RuntimeException ex) {
                LOGGER.warn("payments.efi.charge_lookup_deferred store_id={} subscription_id={} type={}",
                        storeId, id, ex.getClass().getSimpleName());
            }
            synchronize(local, remote, invoice, "EFI_CHECKOUT");
            if (invoice != null && "unpaid".equalsIgnoreCase(invoice.paymentStatus())) {
                throw new PaymentGatewayException("O cartão não foi aprovado pela Efí. Confira os dados ou use outro cartão.");
            }
        } catch (RuntimeException ex) {
            local.setLastError(truncate(ex.getMessage()));
            local.setLastSyncedAt(Instant.now());
            subscriptions.save(local);
            throw ex;
        }
    }

    private static boolean validPayer(EfiGateway.EfiPayer payer) {
        return hasText(payer.name()) && payer.name().length() <= 120
                && payer.cpf() != null && payer.cpf().matches("\\d{11}")
                && payer.email() != null && payer.email().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
                && payer.phone() != null && payer.phone().matches("\\d{10,11}")
                && payer.birth() != null && payer.birth().matches("\\d{4}-\\d{2}-\\d{2}");
    }

    public BigDecimal amount(Store store, PlanType plan) {
        PaymentSubscription existing = subscriptions.findByStoreId(store.getStoreId()).orElse(null);
        if (existing != null && (existing.getProvider().isStripeOrCreem() || existing.getProvider().isDodo() || existing.getProvider() == PaymentProviderType.DLOCAL_GO) && existing.isAccessActive() && existing.getPlan() == plan)
            return existing.getAmountValue();
        try { return priceGateway(store).map(gateway -> amount(gateway, store, plan)).orElse(BigDecimal.ZERO); }
        catch (IllegalArgumentException ex) { return BigDecimal.ZERO; }
    }

    public boolean analyticsSandbox(Store store) {
        return priceGateway(store).map(gateway -> gateway.environment() == br.com.nuvemcustomfields.entity.PaymentEnvironment.SANDBOX).orElse(false);
    }

    public String currency(Store store) {
        PaymentSubscription existing = subscriptions.findByStoreId(store.getStoreId()).orElse(null);
        if (existing != null && (existing.getProvider().isStripeOrCreem() || existing.getProvider().isDodo() || existing.getProvider() == PaymentProviderType.DLOCAL_GO) && existing.isAccessActive()) return existing.getCurrency();
        return priceGateway(store).map(gateway -> gateway.currency(store)).orElse(store.getStoreCurrency());
    }

    private Optional<PaymentGateway> priceGateway(Store store) {
        PaymentSubscription existing = subscriptions.findByStoreId(store.getStoreId()).orElse(null);
        if (existing != null && existing.isAccessActive() && router.operational(existing.getProvider()))
            return Optional.of(router.require(existing.getProvider()));
        return router.forStore(store);
    }

    public Optional<PaymentSubscription> find(Long storeId) {
        return subscriptions.findByStoreId(storeId);
    }

    public boolean planAvailable(Store store, PlanType plan) {
        try {
            PaymentSubscription existing = subscriptions.findByStoreId(store.getStoreId()).orElse(null);
            // An existing sandbox contract can remain active after its test gateway
            // is disabled. Show plans from the current market route in that case,
            // rather than hiding the entire comparison screen.
            PaymentGateway gateway = existing != null && existing.isAccessActive() && router.configured(existing.getProvider())
                    ? router.require(existing.getProvider()) : router.requireForStore(store);
            if (existing != null && existing.isAccessActive() && existing.getProvider().isStripeOrCreem()
                    && !existing.getCurrency().equals(gateway.currency(store))) return false;
            return gateway.planAvailable(store, plan);
        } catch (RuntimeException ex) { return false; }
    }

    public BigDecimal upgradeAmount(Store store, PlanType plan) {
        PaymentSubscription existing = subscriptions.findByStoreId(store.getStoreId()).orElseThrow();
        return amount(router.require(existing.getProvider()), store, plan);
    }

    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public boolean upgrade(Long storeId, PlanType targetPlan, BigDecimal confirmedAmount) {
        if (creemUpgrade != null && subscriptions.findByStoreId(storeId)
                .map(local -> local.getProvider() == PaymentProviderType.CREEM).orElse(false)) {
            return creemUpgrade.upgrade(storeId, targetPlan, confirmedAmount);
        }
        Store store = stores.findActiveByStoreIdForUpdate(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Loja ativa nao encontrada."));
        PaymentSubscription subscription = subscriptions.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Não há assinatura ativa para alterar."));
        if (targetPlan == null || !subscription.isAccessActive()
                || subscription.getStatus() != PaymentSubscriptionStatus.ACTIVE
                || subscription.isCancellationPending() || subscription.getCancellationEffectiveAt() != null
                || subscription.isWinbackRestorePending() || store.isCourtesyPremium()
                || !targetPlan.isUpgradeFrom(subscription.getPlan())
                || !hasText(subscription.getProviderSubscriptionId())) {
            throw new IllegalArgumentException("Selecione um plano superior ao plano atual.");
        }
        if (subscription.getProvider() == PaymentProviderType.DLOCAL_GO)
            throw new IllegalArgumentException("Revise o ajuste proporcional na página de upgrade dLocal Go antes de confirmar.");
        if (subscription.getProvider().isDodo())
            throw new IllegalArgumentException("O Dodo ainda não oferece alteração de plano.");
        if (subscription.getProvider() == PaymentProviderType.STRIPE)
            throw new IllegalArgumentException("Para alterar o plano Stripe, entre em contato com o suporte.");
        PaymentGateway gateway = router.require(subscription.getProvider());
        if (subscription.getProviderEnvironment() != gateway.environment() || !gateway.planAvailable(store, targetPlan)) {
            throw new IllegalArgumentException("Plano ainda não disponível para esta assinatura.");
        }
        if (subscription.getUpgradePlan() != null) {
            reconcile(storeId);
            throw new PaymentGatewayException("A alteração anterior está sendo confirmada. Atualize a página antes de tentar novamente.");
        }
        BigDecimal targetAmount = amount(gateway, store, targetPlan);
        if (confirmedAmount == null || targetAmount.compareTo(confirmedAmount) != 0) {
            throw new IllegalArgumentException("O preço mudou. Revise o valor antes de confirmar.");
        }
        GatewaySubscription before = gateway.getSubscription(subscription.getProviderSubscriptionId());
        validateRemote(subscription, before);
        if (!"active".equalsIgnoreCase(before.status()) && !"authorized".equalsIgnoreCase(before.status())
                && !"new_charge".equalsIgnoreCase(before.status())) {
            throw new IllegalArgumentException("A assinatura precisa estar ativa para fazer upgrade.");
        }
        subscription.captureAnalytics();
        if (subscription.getAnalyticsFirstPaymentId() == null) subscription.setAnalyticsFirstPaymentId("legacy");
        subscription.requestUpgrade(targetPlan, targetAmount, gateway.priceId(store, targetPlan));
        subscriptions.saveAndFlush(subscription);
        try {
            gateway.changeSubscriptionPlan(subscription.getProviderSubscriptionId(), store, targetPlan);
            GatewaySubscription remote = gateway.getSubscription(subscription.getProviderSubscriptionId());
            if (!matchesUpgrade(subscription, remote))
                throw new PaymentGatewayException("A alteração está aguardando confirmação do provedor.");
            synchronize(subscription, remote, subscription.getProvider().isStripeOrCreem() ? gateway.getLatestInvoice(remote.id()).orElse(null) : null, "PAYMENT_UPGRADE");
        } catch (RuntimeException ex) {
            throw new PaymentGatewayException("A alteração precisa ser confirmada pelo provedor. Seu acesso atual foi preservado.", ex);
        }
        return true;
    }

    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public String startCheckout(Long storeId, PlanType plan) {
        if (plan == null || !plan.isBillable()) {
            throw new IllegalArgumentException("Selecione um plano pago.");
        }
        if (dlocalGoCheckout != null) {
            Store candidate = stores.findByStoreId(storeId).orElseThrow(() -> new IllegalArgumentException("Loja não encontrada."));
            if (router.forStore(candidate).map(g -> g.provider() == PaymentProviderType.DLOCAL_GO).orElse(false))
                return dlocalGoCheckout.start(storeId, plan);
        }
        if (stripeCheckout != null) {
            Store candidate = stores.findByStoreId(storeId).orElseThrow(() -> new IllegalArgumentException("Loja não encontrada."));
            if (router.forStore(candidate).map(g -> g.provider() == PaymentProviderType.STRIPE).orElse(false))
                return stripeCheckout.start(storeId, plan);
        }
        if (dodoLiveCheckout != null) {
            Store candidate = stores.findByStoreId(storeId).orElseThrow(() -> new IllegalArgumentException("Loja não encontrada."));
            if (router.forStore(candidate).map(g -> g.provider() == PaymentProviderType.DODO).orElse(false))
                return dodoLiveCheckout.start(storeId, plan);
        }
        if (dodoTestCheckout != null) {
            Store candidate = stores.findByStoreId(storeId).orElseThrow(() -> new IllegalArgumentException("Loja não encontrada."));
            if (router.forStore(candidate).map(g -> g.provider() == PaymentProviderType.DODO_TEST).orElse(false))
                return dodoTestCheckout.start(storeId, plan);
        }
        if (creemCheckout != null) {
            Store candidate = stores.findByStoreId(storeId).orElseThrow(() -> new IllegalArgumentException("Loja não encontrada."));
            if (router.forStore(candidate).map(g -> g.provider() == PaymentProviderType.CREEM).orElse(false))
                return creemCheckout.start(storeId, plan);
        }
        Store store = stores.findActiveByStoreIdForUpdate(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Loja ativa nao encontrada."));
        refreshProfileIfNeeded(store);
        PaymentGateway gateway = router.requireForStore(store);
        if (gateway.provider() == PaymentProviderType.DLOCAL_GO)
            throw new PaymentGatewayException("Reabra os planos para iniciar o checkout dLocal Go.");
        if (gateway.provider().isDodo())
            throw new PaymentGatewayException("Reabra os planos para iniciar o checkout Dodo de teste.");
        if (gateway.provider() == PaymentProviderType.STRIPE)
            throw new PaymentGatewayException("Reabra os planos para iniciar o checkout Stripe.");
        if (gateway.provider() == PaymentProviderType.CREEM)
            throw new PaymentGatewayException("O perfil da loja foi atualizado. Reabra os planos para iniciar o checkout Creem.");
        PaymentSubscription subscription = subscriptions.findByStoreId(storeId).orElse(null);
        if (plan == PlanType.PREMIUM_ULTRA && !gateway.planAvailable(store, plan))
            throw new IllegalArgumentException("O Ultra ainda não está disponível para assinatura.");
        if (subscription != null && subscription.isWinbackRestorePending()) {
            restoreWinbackAmount(subscription);
            if (subscription.isWinbackRestorePending())
                throw new PaymentGatewayException("A assinatura anterior precisa ser conciliada antes de uma nova cobrança.");
        }
        if (subscription != null && subscription.isAccessActive()) {
            throw new IllegalArgumentException("A loja ja possui uma assinatura ativa.");
        }
        if (attempts != null) {
            Optional<PaymentAttempt> previousAttempt = attempts.findFirstByStoreIdAndStatusInOrderByCreatedAtDesc(storeId,
                    java.util.EnumSet.of(PaymentAttemptStatus.CREATING, PaymentAttemptStatus.UNKNOWN));
            if (previousAttempt.isPresent()) {
                PaymentAttempt attempt = previousAttempt.get();
                if (defaultCheckoutUrlMissing(attempt.getLastError())) {
                    attempt.setStatus(PaymentAttemptStatus.FAILED);
                    attempts.save(attempt);
                } else {
                    throw new IllegalStateException("A criação anterior ainda precisa ser conciliada. Nenhuma nova cobrança foi iniciada.");
                }
            }
        }
        if (subscription != null && subscription.getStatus() == PaymentSubscriptionStatus.PENDING
                && plan == subscription.getPlan()
                && hasText(subscription.getProviderCheckoutId())
                && hasText(subscription.getCheckoutUrl())
                && reusableCheckout(subscription)) {
            return subscription.getCheckoutUrl();
        }
        if (subscription != null && subscription.getStatus() == PaymentSubscriptionStatus.PENDING) {
            PaymentGateway previousGateway = router.require(subscription.getProvider());
            if (hasText(subscription.getProviderSubscriptionId())) {
                previousGateway.cancel(subscription.getProviderSubscriptionId());
                subscription.setProviderSubscriptionId(null);
            }
            if (hasText(subscription.getProviderCheckoutId())) {
                previousGateway.cancelCheckout(subscription.getProviderCheckoutId());
                subscription.setProviderCheckoutId(null);
            }
        }

        if (subscription == null) {
            subscription = new PaymentSubscription();
            subscription.setStoreId(storeId);
        }
        String reference = "ncf_" + storeId + "_" + UUID.randomUUID().toString().replace("-", "");
        PaymentAttempt attempt = null;
        subscription.applyWinbackCoupon(null, null, null);
        subscription.setProvider(gateway.provider());
        subscription.setPayerEmail(null);
        subscription.setProviderSubscriptionId(null);
        subscription.setProviderCheckoutId(null);
        subscription.setExternalReference(reference);
        subscription.captureAnalytics();
        subscription.setAnalyticsFirstPaymentId(null);
        subscription.setPlan(plan);
        subscription.setCurrency(gateway.currency(store));
        subscription.setAmountValue(amount(gateway, store, plan));
        subscription.setCountryCode(store.getStoreCountryCode());
        subscription.setProviderEnvironment(gateway.environment());
        subscription.setProviderPriceId(gateway.priceId(store, plan));
        subscription.setStatus(PaymentSubscriptionStatus.PENDING);
        subscription.setPendingStartedAt(Instant.now());
        subscription.setProviderStatus("pending");
        subscription.setCheckoutUrl(null);
        subscription.setLastError(null);
        subscription.setTechnicalError(null);
        subscriptions.saveAndFlush(subscription);

        try {
            String returnUrl = nuvemshopProperties.appBaseUrl() + "/admin/billing/return";
            GatewayCheckout checkout = gateway.createCheckout(store, plan, reference, returnUrl);
            subscription.setProviderSubscriptionId(checkout.subscriptionId());
            subscription.setProviderCheckoutId(checkout.checkoutResourceId());
            String checkoutUrl = checkout.checkoutUrl();
            if (attempt != null) {
                attempt.setProviderTransactionId(checkout.checkoutResourceId());
                attempt.setStatus(PaymentAttemptStatus.OPEN);
                attempts.save(attempt);
            }
            subscription.setCheckoutUrl(checkoutUrl);
            subscription.setProviderStatus(checkout.providerStatus());
            subscription.setLastSyncedAt(Instant.now());
            subscriptions.save(subscription);
            LOGGER.info("payments.checkout.created store_id={} provider={} plan={} checkout_id={} subscription_id={}",
                    storeId, gateway.provider(), plan, checkout.checkoutResourceId(), checkout.subscriptionId());
            return checkoutUrl;
        } catch (RuntimeException ex) {
            subscription.setStatus(attempt == null ? PaymentSubscriptionStatus.ERROR : PaymentSubscriptionStatus.PENDING);
            subscription.setLastError(customerFacingError(ex.getMessage()));
            subscription.setTechnicalError(truncate(ex.getMessage()));
            subscription.setLastSyncedAt(Instant.now());
            subscriptions.save(subscription);
            if (attempt != null) {
                attempt.setStatus(defaultCheckoutUrlMissing(ex.getMessage())
                        ? PaymentAttemptStatus.FAILED : PaymentAttemptStatus.UNKNOWN);
                attempt.setLastError(truncate(ex.getMessage()));
                attempts.save(attempt);
            }
            throw ex;
        }
    }

    @Transactional
    public PaymentSubscription reconcile(Long storeId) {
        PaymentSubscription local = subscriptions.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Assinatura local nao encontrada."));
        if (local.getProviderSubscriptionId() == null) {
            if (local.getProvider() == PaymentProviderType.DLOCAL_GO && hasText(local.getProviderCheckoutId()))
                return synchronizeDlocalGoCheckout(local);
            if (local.getProvider() == PaymentProviderType.STRIPE && hasText(local.getProviderCheckoutId()))
                return synchronizeStripeCheckout(local.getProviderCheckoutId());
            if (local.getProvider().isDodo() && hasText(local.getProviderCheckoutId()))
                return synchronizeDodoCheckout(local);
            if (local.getProvider().isStripeOrCreem() && hasText(local.getProviderCheckoutId()))
                return synchronizeCreemCheckout(local.getProviderCheckoutId());
            return local;
        }
        PaymentGateway gateway = router.require(local.getProvider());
        GatewaySubscription remote = gateway.getSubscription(local.getProviderSubscriptionId());
        validateRemote(local, remote);
        if (local.isCancellationPending()) {
            if (!canceled(remote.status()) && remote.cancellationEffectiveAt() == null) {
                gateway.cancel(local.getProviderSubscriptionId());
                remote = gateway.getSubscription(local.getProviderSubscriptionId());
                validateRemote(local, remote);
            }
            if (!canceled(remote.status()) && remote.cancellationEffectiveAt() == null) {
                throw new PaymentGatewayException("Aguardando a confirmação do cancelamento pelo provedor.");
            }
        }
        Optional<GatewayInvoice> invoice = canceled(remote.status()) && local.getProvider() != PaymentProviderType.EFI
                && local.getProvider() != PaymentProviderType.DLOCAL_GO
                ? Optional.empty() : latestInvoice(gateway, local, remote);
        if (local.getProvider() == PaymentProviderType.EFI && local.getStatus() == PaymentSubscriptionStatus.PENDING
                && canceled(remote.status()) && invoice.isPresent()
                && !"canceled".equalsIgnoreCase(invoice.get().paymentStatus())) {
            // A assinatura pode estar cancelada enquanto a cobranca ainda aguarda.
            // Nao libere outra tentativa ate confirmar tambem o cancelamento dela.
            return local;
        }
        return synchronize(local, remote, invoice.orElse(null), "PAYMENT_RECONCILE");
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean expirePendingCheckout(Long storeId) {
        if (stores.findActiveByStoreIdForUpdate(storeId).isEmpty()) return false;
        PaymentSubscription local = subscriptions.findByStoreId(storeId).orElse(null);
        if (local == null || local.getStatus() != PaymentSubscriptionStatus.PENDING || local.isAccessActive()) return false;
        if (local.getProvider() == PaymentProviderType.DLOCAL_GO) {
            if (!hasText(local.getProviderCheckoutId())) return false;
            PaymentSubscription reconciled = synchronizeDlocalGoCheckout(local);
            if (reconciled.getStatus() != PaymentSubscriptionStatus.PENDING) return true;
            if (pendingExpired(reconciled, Instant.now()) && reconciled.getProviderSubscriptionId() == null) {
                router.require(PaymentProviderType.DLOCAL_GO).cancelCheckout(reconciled.getProviderCheckoutId());
                // Fetch again after deactivation: a payment may have completed concurrently.
                reconciled = synchronizeDlocalGoCheckout(reconciled);
            }
            return reconciled.getStatus() != PaymentSubscriptionStatus.PENDING;
        }
        if (local.getProvider() == PaymentProviderType.STRIPE) {
            if (!hasText(local.getProviderCheckoutId())) return false;
            return synchronizeStripeCheckout(local.getProviderCheckoutId()).getStatus() != PaymentSubscriptionStatus.PENDING;
        }
        if (local.getProvider() == PaymentProviderType.EFI) return expirePendingEfi(storeId);
        if (local.getProvider().isDodo()) {
            if (!hasText(local.getProviderCheckoutId())) return false;
            return synchronizeDodoCheckout(local).getStatus() != PaymentSubscriptionStatus.PENDING;
        }
        if (local.getProvider().isStripeOrCreem()) {
            if (!hasText(local.getProviderCheckoutId())) return false; // Unknown response must be reconciled, never replayed.
            PaymentSubscription reconciled = synchronizeCreemCheckout(local.getProviderCheckoutId());
            return reconciled.getStatus() != PaymentSubscriptionStatus.PENDING;
        }
        return false;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void preparePendingCheckout(Long storeId, PlanType requestedPlan) {
        expirePendingCheckout(storeId);
        PaymentSubscription local = subscriptions.findByStoreId(storeId).orElse(null);
        // Older Dodo adapters persisted terminal failed/expired contracts as PAUSED.
        // Read authenticated provider state before deciding whether a new purchase is safe.
        if (local != null && local.getProvider().isDodo() && !local.isAccessActive()
                && hasText(local.getProviderSubscriptionId())
                && java.util.EnumSet.of(PaymentSubscriptionStatus.PAUSED, PaymentSubscriptionStatus.ERROR,
                        PaymentSubscriptionStatus.PAST_DUE).contains(local.getStatus())) {
            requireProvider(local, local.getProvider());
            local = reconcile(storeId);
        }
        if (local == null || local.getStatus() != PaymentSubscriptionStatus.PENDING || local.isAccessActive()
                || local.getPlan() == requestedPlan) return;
        // A checkout may already have created an unpaid subscription. Reconcile it before closing it.
        if (hasText(local.getProviderSubscriptionId())) {
            local = reconcile(storeId);
            if (local.isAccessActive() || local.getStatus() != PaymentSubscriptionStatus.PENDING) return;
            PaymentGateway previous = router.require(local.getProvider());
            previous.cancelImmediately(local.getProviderSubscriptionId());
            reconcile(storeId);
            return;
        }
        if (local.getProvider() != PaymentProviderType.DLOCAL_GO && local.getProvider() != PaymentProviderType.STRIPE) return;
        // An unknown creation response must still be reconciled before replacement.
        if (!hasText(local.getProviderCheckoutId())) return;
        router.require(local.getProvider()).cancelCheckout(local.getProviderCheckoutId());
        // A payment can finish while the checkout is being closed. Read authenticated state again.
        if (local.getProvider() == PaymentProviderType.DLOCAL_GO) synchronizeDlocalGoCheckout(local);
        else synchronizeStripeCheckout(local.getProviderCheckoutId());
    }

    private PaymentSubscription synchronizeDlocalGoCheckout(PaymentSubscription local) {
        requireProvider(local, PaymentProviderType.DLOCAL_GO);
        var gateway = (br.com.nuvemcustomfields.payment.DlocalGoGateway) router.require(PaymentProviderType.DLOCAL_GO);
        var remote = gateway.checkoutSubscription(local.getProviderCheckoutId());
        if (remote.isPresent()) return synchronize(local, remote.get(), gateway.getLatestInvoice(remote.get().id()).orElse(null), "DLOCAL_GO_CHECKOUT");
        if (!gateway.getPlan(local.getProviderCheckoutId()).path("active").asBoolean())
            markExpiredCheckout(local, "canceled", "O checkout dLocal Go foi encerrado. Você pode tentar novamente.");
        return local;
    }

    private void markExpiredCheckout(PaymentSubscription local, String providerStatus, String message) {
        local.setStatus(PaymentSubscriptionStatus.CANCELED);
        local.setProviderStatus(providerStatus);
        local.setCheckoutUrl(null);
        local.setLastError(message);
        local.setTechnicalError(null);
        if (attempts != null) attempts.findByExternalReference(local.getExternalReference()).ifPresent(attempt -> {
            attempt.setStatus(PaymentAttemptStatus.CANCELED); attempts.save(attempt);
        });
        subscriptions.save(local);
        LOGGER.info("payments.checkout.expired store_id={} provider={} checkout_id={} status={}",
                local.getStoreId(), local.getProvider(), local.getProviderCheckoutId(), providerStatus);
    }

    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public boolean expirePendingEfi(Long storeId) {
        if (stores.findActiveByStoreIdForUpdate(storeId).isEmpty()) return false;
        PaymentSubscription local = subscriptions.findByStoreId(storeId).orElse(null);
        if (local == null || local.getProvider() != PaymentProviderType.EFI
                || local.getStatus() != PaymentSubscriptionStatus.PENDING || local.isAccessActive()
                || !pendingExpired(local, Instant.now())) return false;
        if (!hasText(local.getProviderSubscriptionId())) {
            if (hasText(local.getLastPaymentId())) {
                throw new PaymentGatewayException("Pagamento anterior sem assinatura identificada. Contate o suporte.");
            }
            local.setStatus(PaymentSubscriptionStatus.ERROR);
            local.setProviderStatus("timeout");
            local.setLastError(EXPIRED_ATTEMPT_MESSAGE);
            subscriptions.save(local);
            return true;
        }
        if (!efi.configured()) {
            throw new PaymentGatewayException("Não foi possível verificar a tentativa anterior na Efí. Contate o suporte.");
        }
        GatewaySubscription remote = efi.getSubscription(local.getProviderSubscriptionId());
        validateRemote(local, remote);
        GatewayInvoice invoice = latestInvoice(efi, local, remote).orElse(null);
        if (invoice != null && invoice.approved()) {
            if ("active".equalsIgnoreCase(remote.status()) || "new_charge".equalsIgnoreCase(remote.status())) {
                synchronize(local, remote, invoice, "PENDING_TIMEOUT_RECONCILE");
            }
            // Um pagamento aprovado nunca deve ser cancelado automaticamente, mesmo que
            // o estado da assinatura ainda nao tenha acompanhado a cobranca.
            return true;
        }
        if (invoice == null && !canceled(remote.status())) {
            throw new PaymentGatewayException("A Efí ainda não informou a cobrança da assinatura. Tente novamente mais tarde.");
        }
        if (invoice != null && !"canceled".equalsIgnoreCase(invoice.paymentStatus())) {
            String chargeStatus = invoice.paymentStatus();
            if (!"new".equalsIgnoreCase(chargeStatus) && !"waiting".equalsIgnoreCase(chargeStatus)
                    && !"unpaid".equalsIgnoreCase(chargeStatus)) {
                throw new PaymentGatewayException("A cobrança anterior ainda precisa ser verificada pela Efí.");
            }
            efi.cancelCharge(invoice.paymentId());
            invoice = efi.getCharge(local.getProviderSubscriptionId(), invoice.paymentId());
            if (invoice.approved()) return true;
            if (!"canceled".equalsIgnoreCase(invoice.paymentStatus())) {
                throw new PaymentGatewayException("Aguardando confirmação do cancelamento da cobrança pela Efí.");
            }
        }
        if (!canceled(remote.status())) {
            efi.cancel(local.getProviderSubscriptionId());
            remote = efi.getSubscription(local.getProviderSubscriptionId());
            validateRemote(local, remote);
            if (!canceled(remote.status())) {
                throw new PaymentGatewayException("Aguardando confirmação do cancelamento da assinatura pela Efí.");
            }
        }
        synchronize(local, remote, invoice, "PENDING_TIMEOUT");
        local.setNextPaymentAt(null);
        local.setLastError(EXPIRED_ATTEMPT_MESSAGE);
        subscriptions.save(local);
        LOGGER.info("payments.efi.pending_expired store_id={} subscription_id={} charge_id={}",
                storeId, local.getProviderSubscriptionId(), invoice == null ? "none" : invoice.paymentId());
        return true;
    }

    private PaymentSubscription synchronizeDodoCheckout(PaymentSubscription local) {
        var gateway = (br.com.nuvemcustomfields.payment.DodoGateway) router.require(local.getProvider());
        requireProvider(local, local.getProvider());
        var checkout = gateway.getCheckout(local.getProviderCheckoutId());
        if (!Objects.equals(local.getProviderCheckoutId(), br.com.nuvemcustomfields.payment.DodoGateway.text(checkout, "id")))
            throw new IllegalArgumentException("Checkout Dodo divergente da tentativa local.");
        // The redirect/session status never grants access. Payment + subscription are fetched server-side.
        String paymentId = br.com.nuvemcustomfields.payment.DodoGateway.text(checkout, "payment_id");
        if (hasText(paymentId)) return synchronizeFromInvoice(local.getProvider(), paymentId);
        return local;
    }

    private static boolean pendingExpired(PaymentSubscription local, Instant now) {
        return local.getPendingStartedAt() != null
                && !now.isBefore(local.getPendingStartedAt().plus(PENDING_TIMEOUT_MINUTES, ChronoUnit.MINUTES));
    }

    @Transactional
    public PaymentSubscription synchronizeFromSubscription(PaymentProviderType provider, String subscriptionId) {
        PaymentGateway gateway = router.require(provider);
        GatewaySubscription remote = gateway.getSubscription(subscriptionId);
        PaymentSubscription replaced = reconcileReplacedPayment(gateway, remote, null);
        if (replaced != null) return replaced;
        PaymentSubscription local = locate(remote);
        if (provider.isStripeOrCreem() || provider.isDodo() || provider == PaymentProviderType.DLOCAL_GO) requireProvider(local, provider);
        return synchronize(local, remote, latestInvoice(gateway, local, remote).orElse(null), "PAYMENT_WEBHOOK");
    }

    @Transactional
    public PaymentSubscription synchronizeFromSubscriptionWithoutPayment(PaymentProviderType provider, String subscriptionId) {
        PaymentGateway gateway = router.require(provider);
        GatewaySubscription remote = gateway.getSubscription(subscriptionId);
        PaymentSubscription replaced = reconcileReplacedPayment(gateway, remote, null);
        if (replaced != null) return replaced;
        PaymentSubscription local = locate(remote);
        if (provider.isStripeOrCreem() || provider.isDodo() || provider == PaymentProviderType.DLOCAL_GO) requireProvider(local, provider);
        return synchronize(local, remote, null, "PAYMENT_WEBHOOK");
    }

    private Optional<GatewayInvoice> latestInvoice(PaymentGateway gateway, PaymentSubscription local,
                                                    GatewaySubscription remote) {
        Optional<GatewayInvoice> invoice = gateway.getLatestInvoice(remote.id());
        if (invoice.isEmpty() && gateway instanceof EfiGateway efiGateway
                && hasText(local.getLastPaymentId())) {
            return Optional.of(efiGateway.getCharge(remote.id(), local.getLastPaymentId()));
        }
        return invoice;
    }

    @Transactional
    public PaymentSubscription synchronizeFromInvoice(PaymentProviderType provider, String invoiceId) {
        PaymentGateway gateway = router.require(provider);
        GatewayInvoice invoice = gateway.getInvoice(invoiceId);
        GatewaySubscription remote = gateway.getSubscription(invoice.subscriptionId());
        PaymentSubscription replaced = reconcileReplacedPayment(gateway, remote, invoice);
        if (replaced != null) return replaced;
        PaymentSubscription local = locate(remote);
        if (provider.isStripeOrCreem() || provider.isDodo() || provider == PaymentProviderType.DLOCAL_GO) requireProvider(local, provider);
        return synchronize(local, remote, invoice, "PAYMENT_WEBHOOK");
    }

    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public void cancel(Long storeId) {
        stores.findByStoreIdForUpdate(storeId);
        PaymentSubscription local = subscriptions.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Assinatura nao encontrada."));
        if (local.isUpgradePaymentPending() || local.getUpgradePlan() != null)
            throw new IllegalArgumentException("Aguarde a confirmação do upgrade antes de cancelar. Se precisar, entre em contato com o suporte.");
        if (local.getStatus() == PaymentSubscriptionStatus.CANCELED) return;
        PaymentGateway gateway = router.require(local.getProvider());
        if (local.isAccessActive() && hasText(local.getProviderSubscriptionId())) {
            GatewaySubscription remote = gateway.getSubscription(local.getProviderSubscriptionId());
            validateRemote(local, remote);
            if (remote.nextPaymentAt() != null) local.setNextPaymentAt(remote.nextPaymentAt());
            if (local.getNextPaymentAt() == null) {
                throw new IllegalStateException("Não foi possível confirmar o fim do período pago. Contate o suporte antes de cancelar.");
            }
        }
        local.setCancellationPending(true);
        local.setCancellationRequestedAt(Instant.now());
        subscriptions.saveAndFlush(local);
        try {
            if (hasText(local.getProviderSubscriptionId())) {
                reconcile(storeId);
            } else if (hasText(local.getProviderCheckoutId())) {
                gateway.cancelCheckout(local.getProviderCheckoutId());
                local.setStatus(PaymentSubscriptionStatus.CANCELED);
                local.setProviderStatus("canceled");
                local.setCancellationPending(false);
                subscriptions.saveAndFlush(local);
            } else {
                throw new IllegalArgumentException("Assinatura ainda nao foi criada no provedor.");
            }
        } catch (RuntimeException ex) {
            local.setLastError(truncate(ex.getMessage()));
            subscriptions.save(local);
            throw ex;
        }
    }

    @Transactional
    public void expireCanceledAccess() {
        for (PaymentSubscription local : subscriptions.findByStatusAndAccessActiveTrueAndNextPaymentAtLessThanEqual(
                PaymentSubscriptionStatus.CANCELED, Instant.now())) {
            Store store = stores.findByStoreId(local.getStoreId()).orElse(null);
            deactivate(local, store, "PAID_PERIOD_ENDED");
            subscriptions.save(local);
        }
    }

    @Transactional
    public void revokeAccessAfterUninstall(Long storeId) {
        subscriptions.findByStoreId(storeId).ifPresent(local -> {
            deactivate(local, stores.findByStoreId(storeId).orElse(null), "APP_UNINSTALLED");
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
        if (local.getProvider() == PaymentProviderType.DLOCAL_GO && invoice != null
                && (!Objects.equals(remote.id(), invoice.subscriptionId()) || !Objects.equals(local.getCurrency(), invoice.currency())
                    || (invoice.approved() && (invoice.amountPaid() == null || (local.getAmountValue().compareTo(invoice.amountPaid()) != 0
                        && (dlocalGoUpgrades == null || !dlocalGoUpgrades.getObject().coversInvoice(local,invoice)))))))
            throw new IllegalArgumentException("Pagamento dLocal Go divergente da assinatura.");
        if (local.getProvider().isDodo() && invoice != null
                && !Objects.equals(remote.id(), invoice.subscriptionId()))
            throw new IllegalArgumentException("Pagamento Dodo divergente da assinatura.");
        if (local.getProvider().isStripeOrCreem() && invoice != null
                && (!Objects.equals(local.getProviderSubscriptionId() == null ? remote.id() : local.getProviderSubscriptionId(), invoice.subscriptionId())
                    || !Objects.equals(local.getCurrency(), invoice.currency())))
            throw new IllegalArgumentException("Pagamento " + (local.getProvider() == PaymentProviderType.STRIPE ? "Stripe" : "Creem")
                    + " divergente da assinatura.");
        if (local.getProvider().isStripeOrCreem() && matchesUpgrade(local, remote)
                && (invoice == null || !invoice.approved() || Objects.equals(invoice.paymentId(), local.getLastPaymentId())))
            throw new PaymentGatewayException("Aguardando confirmação do pagamento proporcional Creem.");
        PlanType sourcePlan = local.getPlan();
        boolean upgraded = matchesUpgrade(local, remote);
        if (upgraded) {
            local.setPlan(local.getUpgradePlan());
            local.setAmountValue(local.getUpgradeAmount());
            local.setProviderPriceId(local.getUpgradePriceId());
            local.clearUpgrade();
        }
        local.setProviderSubscriptionId(remote.id());
        if (local.getProvider() == PaymentProviderType.EFI && hasText(remote.checkoutResourceId())) {
            local.setProviderPriceId(remote.checkoutResourceId());
            local.setProviderCheckoutId(null);
        } else if (hasText(remote.checkoutResourceId()) && !hasText(local.getProviderCheckoutId())) {
            local.setProviderCheckoutId(remote.checkoutResourceId());
        }
        if (hasText(remote.customerId())) local.setProviderCustomerId(remote.customerId());
        if (hasText(remote.priceId())) local.setProviderPriceId(remote.priceId());
        if (remote.currentPeriodStart() != null) local.setCurrentPeriodStart(remote.currentPeriodStart());
        if (remote.currentPeriodEnd() != null) local.setCurrentPeriodEnd(remote.currentPeriodEnd());
        if (remote.cancellationEffectiveAt() != null) local.setCancellationEffectiveAt(remote.cancellationEffectiveAt());
        local.setProviderStatus(remote.status());
        if (remote.nextPaymentAt() != null) local.setNextPaymentAt(remote.nextPaymentAt());
        local.setLastSyncedAt(Instant.now());
        local.setLastError(null);
        if (invoice != null) {
            local.setLastPaymentId(invoice.paymentId());
            local.setLastPaymentStatus(invoice.paymentStatus());
            if (winbackDiscounts != null) {
                winbackDiscounts.confirm(local, invoice);
                restoreWinbackAmount(local);
            }
        }
        Store store = stores.findByStoreId(local.getStoreId())
                .orElseThrow(() -> new IllegalArgumentException("Loja da assinatura nao encontrada."));
        if (upgraded) {
            activate(local, store, "PAYMENT_UPGRADE");
            paymentNotifications.planChanged(local, sourcePlan);
        }
        paymentNotifications.paymentFailed(local, invoice);
        String status = remote.status() == null ? "" : remote.status().toLowerCase();
        if (local.getProvider() == PaymentProviderType.DLOCAL_GO && "active".equals(status)
                && invoice != null && invoice.approved()
                && (remote.currentPeriodEnd() == null || !Instant.now().isBefore(remote.currentPeriodEnd()))) {
            local.setStatus(PaymentSubscriptionStatus.PAUSED); deactivate(local, store, source);
        } else if (local.getProvider() == PaymentProviderType.DLOCAL_GO && invoice != null
                && java.util.Set.of("refunded", "chargedBack", "unpaid").contains(invoice.paymentStatus())) {
            local.setStatus(PaymentSubscriptionStatus.PAUSED); deactivate(local, store, source);
        } else if (local.getProvider() == PaymentProviderType.STRIPE && "incomplete_expired".equals(status)) {
            markExpiredCheckout(local, status, "A assinatura Stripe incompleta expirou. Você pode tentar novamente.");
            deactivate(local, store, source);
        } else if (local.getProvider() == PaymentProviderType.STRIPE && "incomplete".equals(status)) {
            local.setStatus(PaymentSubscriptionStatus.PENDING);
            deactivate(local, store, source);
        } else if (local.getProvider() == PaymentProviderType.STRIPE && invoice != null
                && java.util.Set.of("refunded", "chargedBack").contains(invoice.paymentStatus())) {
            local.setStatus(canceled(status) || remote.cancellationEffectiveAt() != null
                    ? PaymentSubscriptionStatus.CANCELED : PaymentSubscriptionStatus.PAUSED);
            if (canceled(status) || remote.cancellationEffectiveAt() != null) local.setCancellationPending(false);
            deactivate(local, store, source);
        } else if (terminalDodoContract(local.getProvider(), status)) {
            // Initial creation failed or the term ended. No recurring contract remains to cancel.
            local.setStatus(PaymentSubscriptionStatus.CANCELED);
            local.setCancellationPending(false);
            local.setCheckoutUrl(null);
            deactivate(local, store, source);
            if (attempts != null) attempts.findByExternalReference(local.getExternalReference()).ifPresent(attempt -> {
                // Retain old reference/checkout identity so late events cannot activate a new purchase.
                attempt.setStatus(PaymentAttemptStatus.CANCELED); attempts.save(attempt);
            });
        } else if (local.getProvider().isDodo()
                && ("paused".equals(status) || "cancelled".equals(status)
                    || remote.currentPeriodEnd() == null || !Instant.now().isBefore(remote.currentPeriodEnd())
                    || (invoice != null && "refunded".equals(invoice.paymentStatus())))) {
            local.setStatus("cancelled".equals(status) ? PaymentSubscriptionStatus.CANCELED : PaymentSubscriptionStatus.PAUSED);
            local.setCancellationPending(false); deactivate(local, store, source);
        } else if (remote.cancellationEffectiveAt() != null) {
            if ((local.getProvider().isStripeOrCreem() || local.getProvider().isDodo() || local.getProvider() == PaymentProviderType.DLOCAL_GO) && invoice != null && invoice.approved()
                    && Instant.now().isBefore(remote.cancellationEffectiveAt())) {
                activate(local, store, source); paymentNotifications.enqueue(local, invoice);
                if (local.getProvider().isDodo() && attempts != null)
                    attempts.findByExternalReference(local.getExternalReference()).ifPresent(attempt -> {
                        attempt.setStatus(PaymentAttemptStatus.COMPLETED); attempts.save(attempt);
                    });
            }
            local.setStatus(PaymentSubscriptionStatus.CANCELED);
            local.setCancellationPending(false);
            local.setCancellationEffectiveAt(remote.cancellationEffectiveAt());
            local.setNextPaymentAt(remote.cancellationEffectiveAt());
        } else if (local.getProvider().isStripeOrCreem() && "active".equals(status)
                && invoice != null && invoice.approved()
                && (remote.currentPeriodEnd() == null || !Instant.now().isBefore(remote.currentPeriodEnd()))) {
            local.setStatus(PaymentSubscriptionStatus.PAUSED); deactivate(local, store, source);
        } else if (local.getProvider().isStripeOrCreem() &&
                ("unpaid".equals(status) || "expired".equals(status) || "trialing".equals(status)
                 || (invoice != null && java.util.Set.of("refunded", "chargedBack").contains(invoice.paymentStatus())))) {
            local.setStatus(PaymentSubscriptionStatus.PAUSED);
            deactivate(local, store, source);
        } else if ("past_due".equals(status) || (local.isAccessActive() && invoice != null && !invoice.approved())) {
            local.setStatus(PaymentSubscriptionStatus.PAST_DUE);
            if (local.getGraceUntil() == null) {
                local.setGraceUntil(Instant.now().plus(graceDays(local.getProvider()), ChronoUnit.DAYS));
            }
            if (!Instant.now().isBefore(local.getGraceUntil())) deactivate(local, store, source);
        } else if (("authorized".equals(status) || "active".equals(status) || "new_charge".equals(status))
                && invoice != null && invoice.approved()) {
            local.setStatus(PaymentSubscriptionStatus.ACTIVE);
            local.setGraceUntil(null);
            local.setCancellationPending(false);
            activate(local, store, source);
            if (upgraded && local.getProvider().isStripeOrCreem()) {
                paymentNotifications.enqueueUpgrade(store, local, sourcePlan, invoice);
            } else {
                paymentNotifications.enqueue(local, invoice);
            }
            if (attempts != null) attempts.findByExternalReference(local.getExternalReference()).ifPresent(attempt -> {
                attempt.setStatus(PaymentAttemptStatus.COMPLETED);
                attempts.save(attempt);
            });
        } else if ("paused".equals(status)) {
            local.setStatus(PaymentSubscriptionStatus.PAUSED);
            deactivate(local, store, source);
        } else if (canceled(status)) {
            if (local.getProvider().isStripeOrCreem()) {
                local.setNextPaymentAt(Instant.now()); deactivate(local, store, source);
            }
            local.setStatus(PaymentSubscriptionStatus.CANCELED);
            local.setCancellationPending(false);
            if (local.getNextPaymentAt() == null || !Instant.now().isBefore(local.getNextPaymentAt())) {
                deactivate(local, store, source);
            }
        } else if (local.getProvider() == PaymentProviderType.EFI && !local.isAccessActive()
                && invoice != null && "unpaid".equalsIgnoreCase(invoice.paymentStatus())) {
            local.setStatus(PaymentSubscriptionStatus.ERROR);
        } else if (!local.isAccessActive()) {
            local.setStatus(PaymentSubscriptionStatus.PENDING);
        }
        if (local.getProvider() == PaymentProviderType.EFI) {
            LOGGER.info("payments.efi.reconciled store_id={} provider_status={} charge_status={} local_status={} source={}",
                    local.getStoreId(), remote.status(), invoice == null ? "missing" : invoice.paymentStatus(),
                    local.getStatus(), source);
        }
        stores.save(store);
        return subscriptions.save(local);
    }

    @Transactional
    public PaymentSubscription synchronizeStripeCheckout(String checkoutId) {
        var gateway = (br.com.nuvemcustomfields.payment.StripeGateway) router.require(PaymentProviderType.STRIPE);
        var checkout = gateway.getCheckout(checkoutId);
        String reference = br.com.nuvemcustomfields.payment.StripeGateway.required(checkout, "client_reference_id");
        PaymentSubscription replaced = replacedCheckoutOwner(reference, PaymentProviderType.STRIPE, gateway.environment());
        if (replaced != null) {
            if (!checkoutId.equals(br.com.nuvemcustomfields.payment.StripeGateway.required(checkout, "id")))
                throw new IllegalArgumentException("Checkout substituído divergente.");
            String remoteId = br.com.nuvemcustomfields.payment.StripeGateway.resourceId(checkout.path("subscription"));
            return hasText(remoteId) ? synchronizeFromSubscription(PaymentProviderType.STRIPE, remoteId) : replaced;
        }
        PaymentSubscription local = subscriptions.findByExternalReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Checkout Stripe sem tentativa local."));
        requireProvider(local, PaymentProviderType.STRIPE);
        var metadata = checkout.path("metadata");
        if (!checkoutId.equals(br.com.nuvemcustomfields.payment.StripeGateway.required(checkout, "id"))
                || !"subscription".equals(br.com.nuvemcustomfields.payment.StripeGateway.text(checkout, "mode"))
                || (hasText(local.getProviderCheckoutId()) && !checkoutId.equals(local.getProviderCheckoutId()))
                || !reference.equals(br.com.nuvemcustomfields.payment.StripeGateway.text(metadata, "external_reference"))
                || !local.getStoreId().toString().equals(br.com.nuvemcustomfields.payment.StripeGateway.text(metadata, "store_id"))
                || !Objects.equals(local.getProviderPriceId(), br.com.nuvemcustomfields.payment.StripeGateway.text(metadata, "price_id"))
                || !"AR".equals(br.com.nuvemcustomfields.payment.StripeGateway.text(metadata, "country_code"))
                || !local.getPlan().name().equals(br.com.nuvemcustomfields.payment.StripeGateway.text(metadata, "plan")))
            throw new IllegalArgumentException("Checkout Stripe divergente da tentativa local.");
        local.setProviderCheckoutId(checkoutId);
        String subscriptionId = br.com.nuvemcustomfields.payment.StripeGateway.resourceId(checkout.path("subscription"));
        if (hasText(subscriptionId)) {
            GatewaySubscription remote = gateway.getSubscription(subscriptionId);
            return synchronize(local, remote, gateway.getLatestInvoice(subscriptionId).orElse(null), "STRIPE_CHECKOUT");
        }
        if ("expired".equals(br.com.nuvemcustomfields.payment.StripeGateway.text(checkout, "status"))) {
            markExpiredCheckout(local, "expired", "O checkout Stripe expirou. Você pode tentar novamente.");
        } else if (attempts != null) attempts.findByExternalReference(reference).ifPresent(attempt -> {
            attempt.setProviderTransactionId(checkoutId);
            if (attempt.getStatus() != PaymentAttemptStatus.COMPLETED) attempt.setStatus(PaymentAttemptStatus.OPEN);
            attempts.save(attempt);
        });
        return subscriptions.save(local);
    }

    @Transactional
    public PaymentSubscription synchronizeCreemCheckout(String checkoutId) {
        var gateway = (br.com.nuvemcustomfields.payment.CreemGateway) router.require(PaymentProviderType.CREEM);
        var checkout = gateway.getCheckout(checkoutId);
        String reference = br.com.nuvemcustomfields.payment.CreemGateway.required(checkout, "request_id");
        PaymentSubscription replaced = replacedCheckoutOwner(reference, PaymentProviderType.CREEM, gateway.environment());
        if (replaced != null) {
            if (!checkoutId.equals(br.com.nuvemcustomfields.payment.CreemGateway.required(checkout, "id")))
                throw new IllegalArgumentException("Checkout substituído divergente.");
            String remoteId = br.com.nuvemcustomfields.payment.CreemGateway.resourceId(checkout.path("subscription"));
            return hasText(remoteId) ? synchronizeFromSubscription(PaymentProviderType.CREEM, remoteId) : replaced;
        }
        PaymentSubscription local = subscriptions.findByExternalReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Checkout Creem sem tentativa local."));
        requireProvider(local, PaymentProviderType.CREEM);
        if (local.getProviderEnvironment() != gateway.environment()
                || (hasText(local.getProviderCheckoutId()) && !Objects.equals(local.getProviderCheckoutId(), checkoutId))
                || !Objects.equals(local.getProviderPriceId(), br.com.nuvemcustomfields.payment.CreemGateway.resourceId(checkout.path("product")))
                || !Objects.equals(local.getStoreId().toString(), br.com.nuvemcustomfields.payment.CreemGateway.text(checkout.path("metadata"), "store_id")))
            throw new IllegalArgumentException("Checkout Creem divergente da tentativa local.");
        local.setProviderCheckoutId(checkoutId);
        if (attempts != null) attempts.findByExternalReference(reference).ifPresent(attempt -> {
            attempt.setProviderTransactionId(checkoutId);
            if (attempt.getStatus() != PaymentAttemptStatus.COMPLETED) attempt.setStatus(PaymentAttemptStatus.OPEN);
            attempts.save(attempt);
        });
        String id = br.com.nuvemcustomfields.payment.CreemGateway.resourceId(checkout.path("subscription"));
        if (hasText(id)) {
            GatewaySubscription remote = gateway.getSubscription(id);
            return synchronize(local, remote, gateway.getLatestInvoice(id).orElse(null), "CREEM_CHECKOUT");
        }
        if ("expired".equals(br.com.nuvemcustomfields.payment.CreemGateway.text(checkout, "status"))) {
            local.setStatus(PaymentSubscriptionStatus.CANCELED); local.setCheckoutUrl(null);
            if (attempts != null) attempts.findByExternalReference(reference).ifPresent(attempt -> {
                attempt.setStatus(PaymentAttemptStatus.CANCELED); attempts.save(attempt);
            });
        }
        return subscriptions.save(local);
    }

    private void requireProvider(PaymentSubscription local, PaymentProviderType provider) {
        if (local.getProvider() != provider || local.getProviderEnvironment() != router.require(provider).environment())
            throw new IllegalArgumentException("Gateway ou ambiente da assinatura divergente.");
    }

    private PaymentSubscription replacedCheckoutOwner(String reference, PaymentProviderType provider, PaymentEnvironment environment) {
        if (attempts == null) return null;
        PaymentAttempt attempt = attempts.findByExternalReference(reference).orElse(null);
        if (attempt == null || !java.util.EnumSet.of(PaymentAttemptStatus.REPLACED,
                PaymentAttemptStatus.REPLACED_PAID, PaymentAttemptStatus.REPLACED_CLOSED,
                PaymentAttemptStatus.CANCELED).contains(attempt.getStatus())) return null;
        if (attempt.getProvider() != provider || attempt.getEnvironment() != environment)
            throw new IllegalArgumentException("Provedor ou ambiente da tentativa substituída divergente.");
        stores.findByStoreIdForUpdate(attempt.getStoreId());
        PaymentSubscription current = subscriptions.findByStoreId(attempt.getStoreId()).orElseThrow();
        return Objects.equals(reference, current.getExternalReference()) ? null : current;
    }

    @Transactional
    public void reconcileReplacedCheckout(String reference) {
        if (attempts == null) return;
        PaymentAttempt attempt = attempts.findByExternalReference(reference).orElseThrow();
        stores.findByStoreIdForUpdate(attempt.getStoreId());
        if (attempt.getStatus() != PaymentAttemptStatus.REPLACED || !hasText(attempt.getProviderTransactionId())) return;
        PaymentGateway gateway = router.require(attempt.getProvider());
        if (attempt.getEnvironment() != gateway.environment()) return;
        String checkoutId = attempt.getProviderTransactionId();
        com.fasterxml.jackson.databind.JsonNode checkout;
        String subscriptionId;
        if (gateway instanceof br.com.nuvemcustomfields.payment.DodoGateway dodo) {
            checkout = dodo.getCheckout(checkoutId);
            if (!checkoutId.equals(br.com.nuvemcustomfields.payment.DodoGateway.text(checkout, "id")))
                throw new IllegalArgumentException("Checkout substituído divergente.");
            String paymentId = br.com.nuvemcustomfields.payment.DodoGateway.text(checkout, "payment_id");
            if (hasText(paymentId)) {
                GatewayInvoice invoice = gateway.getInvoice(paymentId);
                GatewaySubscription remote = gateway.getSubscription(invoice.subscriptionId());
                if (!reference.equals(remote.externalReference())) throw new IllegalArgumentException("Referência substituída divergente.");
                reconcileReplacedPayment(gateway, remote, invoice);
                return;
            }
            subscriptionId = null;
        } else if (gateway instanceof br.com.nuvemcustomfields.payment.CreemGateway creem) {
            checkout = creem.getCheckout(checkoutId);
            if (!checkoutId.equals(br.com.nuvemcustomfields.payment.CreemGateway.text(checkout, "id"))
                    || !reference.equals(br.com.nuvemcustomfields.payment.CreemGateway.text(checkout, "request_id")))
                throw new IllegalArgumentException("Checkout substituído divergente.");
            subscriptionId = br.com.nuvemcustomfields.payment.CreemGateway.resourceId(checkout.path("subscription"));
        } else return;
        if (hasText(subscriptionId)) {
            GatewaySubscription remote = gateway.getSubscription(subscriptionId);
            if (!reference.equals(remote.externalReference())) throw new IllegalArgumentException("Referência substituída divergente.");
            reconcileReplacedPayment(gateway, remote, null);
        } else {
            String status = checkout.path("status").asText(checkout.path("payment_status").asText());
            attempt.setStatus(java.util.Set.of("expired", "cancelled", "canceled").contains(status)
                    ? PaymentAttemptStatus.REPLACED_CLOSED : PaymentAttemptStatus.REPLACED);
            attempts.saveAndFlush(attempt);
        }
    }

    private PaymentSubscription reconcileReplacedPayment(PaymentGateway gateway, GatewaySubscription remote, GatewayInvoice invoice) {
        if (attempts == null || !hasText(remote.externalReference())) return null;
        PaymentAttempt attempt = attempts.findByExternalReference(remote.externalReference()).orElse(null);
        if (attempt == null || !java.util.EnumSet.of(PaymentAttemptStatus.REPLACED,
                PaymentAttemptStatus.REPLACED_PAID, PaymentAttemptStatus.REPLACED_CLOSED,
                PaymentAttemptStatus.CANCELED).contains(attempt.getStatus())) return null;
        stores.findByStoreIdForUpdate(attempt.getStoreId());
        PaymentSubscription current = subscriptions.findByStoreId(attempt.getStoreId()).orElseThrow();
        if (Objects.equals(current.getExternalReference(), remote.externalReference())) return null;
        if (attempt.getProvider() != gateway.provider() || attempt.getEnvironment() != gateway.environment())
            throw new IllegalArgumentException("Provedor ou ambiente da tentativa substituída divergente.");
        if (invoice == null) invoice = gateway.getLatestInvoice(remote.id()).orElse(null);
        if (invoice != null && !Objects.equals(remote.id(), invoice.subscriptionId()))
            throw new IllegalArgumentException("Pagamento divergente da tentativa substituída.");
        if (!canceled(remote.status()) && !terminalDodoContract(gateway.provider(), remote.status())) {
            gateway.cancelImmediately(remote.id());
            if (!canceled(gateway.getSubscription(remote.id()).status()))
                throw new PaymentGatewayException("Cancelamento da assinatura substituída ainda não confirmado.");
        }
        if (invoice != null && invoice.approved()) {
            if (attempt.getStatus() != PaymentAttemptStatus.REPLACED_PAID)
                LOGGER.error("payments.replaced_checkout.paid store_id={} provider={} subscription_id={} payment_id={}",
                        attempt.getStoreId(), attempt.getProvider(), remote.id(), invoice.paymentId());
            attempt.setStatus(PaymentAttemptStatus.REPLACED_PAID);
            attempt.setLastError("Pagamento recebido em checkout substituído. Assinatura encerrada; verificar reembolso no provedor.");
        } else if (attempt.getStatus() != PaymentAttemptStatus.REPLACED_PAID) {
            attempt.setStatus(terminalDodoContract(gateway.provider(), remote.status())
                    ? PaymentAttemptStatus.REPLACED_CLOSED : PaymentAttemptStatus.REPLACED);
        }
        attempts.saveAndFlush(attempt);
        return current;
    }

    private static boolean terminalDodoContract(PaymentProviderType provider, String status) {
        return provider.isDodo() && ("failed".equals(status) || "expired".equals(status));
    }

    private PaymentSubscription locate(GatewaySubscription remote) {
        Optional<PaymentSubscription> local = hasText(remote.id())
                ? subscriptions.findByProviderSubscriptionId(remote.id()) : Optional.empty();
        if (local.isEmpty() && hasText(remote.checkoutResourceId())) {
            local = subscriptions.findByProviderCheckoutId(remote.checkoutResourceId());
        }
        if (local.isEmpty() && hasText(remote.externalReference())) {
            local = subscriptions.findByExternalReference(remote.externalReference());
        }
        return local
                .orElseThrow(() -> new IllegalArgumentException("Assinatura notificada nao pertence a uma loja."));
    }

    private void validateRemote(PaymentSubscription local, GatewaySubscription remote) {
        if (local.getProvider() == PaymentProviderType.STRIPE) {
            requireProvider(local, PaymentProviderType.STRIPE);
            if (!Objects.equals(local.getExternalReference(), remote.externalReference())
                    || !Objects.equals(local.getProviderPriceId(), remote.priceId()))
                throw new IllegalArgumentException("Preço ou referência Stripe divergente da tentativa local.");
        }
        if (hasText(local.getProviderSubscriptionId()) && !Objects.equals(local.getProviderSubscriptionId(), remote.id()))
            throw new IllegalArgumentException("Identificador de assinatura divergente.");
        if (matchesUpgrade(local, remote)) {
            if (hasText(remote.externalReference()) && !Objects.equals(local.getExternalReference(), remote.externalReference()))
                throw new IllegalArgumentException("Referencia externa da assinatura divergente.");
            return;
        }
        if (local.getProvider().isDodo()
                && (!Objects.equals(local.getProviderPriceId(), remote.priceId())
                    || !Objects.equals(local.getExternalReference(), remote.externalReference())))
            throw new IllegalArgumentException("Produto ou referência Dodo divergente da tentativa local.");
        if (local.getProvider().isStripeOrCreem()
                && !Objects.equals(local.getProviderPriceId(), remote.priceId()))
            throw new IllegalArgumentException("Produto Creem divergente da assinatura.");
        if (local.getProvider() == PaymentProviderType.DLOCAL_GO) {
            requireProvider(local, PaymentProviderType.DLOCAL_GO);
            if (!Objects.equals(local.getProviderCheckoutId(), remote.checkoutResourceId())
                    || !Objects.equals(local.getExternalReference(), remote.externalReference()))
                throw new IllegalArgumentException("Checkout ou referência dLocal Go divergente.");
        }
        String expectedResource = hasText(local.getProviderPriceId()) ? local.getProviderPriceId() : local.getProviderCheckoutId();
        if (local.getProvider() != PaymentProviderType.DLOCAL_GO && hasText(expectedResource) && hasText(remote.checkoutResourceId())
                && !Objects.equals(expectedResource, remote.checkoutResourceId())) {
            throw new IllegalArgumentException("Plano de checkout da assinatura divergente.");
        }
        if (hasText(remote.externalReference())
                && !Objects.equals(local.getExternalReference(), remote.externalReference())) {
            throw new IllegalArgumentException("Referencia externa da assinatura divergente.");
        }
        // Dodo can represent a localized recurring checkout in the product base
        // currency even when the authenticated payment was collected in the
        // catalog's regional currency. This is safe only for a product still
        // bound to a validated local catalog entry; product and reference were
        // checked above.
        if (local.getProvider().isDodo() && hasValidatedDodoCatalogPrice(local)) return;
        boolean restoringFullPrice = local.isWinbackRestorePending() && local.getWinbackCouponCode() != null
                && local.getWinbackRegularAmount() != null
                && local.getWinbackRegularAmount().compareTo(remote.amount()) == 0;
        if (!local.getCurrency().equalsIgnoreCase(remote.currency())
                || (local.getAmountValue().compareTo(remote.amount()) != 0 && !restoringFullPrice)) {
            throw new IllegalArgumentException("Valor ou moeda da assinatura divergente.");
        }
    }

    private boolean hasValidatedDodoCatalogPrice(PaymentSubscription local) {
        if (catalog == null || !hasText(local.getCountryCode()) || local.getPlan() == null) return false;
        return catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(
                        local.getProvider(), local.getProviderEnvironment(), local.getCountryCode(), local.getPlan())
                // The subscription retains the product ID it was created with.
                // A later validated catalog refresh may replace that product ID,
                // but must not invalidate an already authenticated checkout.
                .filter(price -> price.isValidated())
                .isPresent();
    }

    private boolean matchesUpgrade(PaymentSubscription local, GatewaySubscription remote) {
        if (local.isUpgradePaymentPending() && !Objects.equals(local.getNextPaymentAt(), remote.nextPaymentAt())) return false;
        if (local.getUpgradePlan() == null || remote.amount() == null
                || !local.getCurrency().equalsIgnoreCase(remote.currency())
                || local.getUpgradeAmount().compareTo(remote.amount()) != 0
                || (!"active".equalsIgnoreCase(remote.status()) && !"authorized".equalsIgnoreCase(remote.status())
                    && !"new_charge".equalsIgnoreCase(remote.status()))) return false;
        String remotePrice = local.getProvider().isStripeOrCreem() ? remote.priceId() : remote.checkoutResourceId();
        return !hasText(local.getUpgradePriceId()) || Objects.equals(local.getUpgradePriceId(), remotePrice);
    }

    private void restoreWinbackAmount(PaymentSubscription local) {
        if (!local.isWinbackRestorePending()) return;
        try {
            winbackDiscounts.restore(local);
        } catch (RuntimeException ex) {
            // The first charge can already be accepted; keep the durable restoration task.
            LOGGER.warn("winback.discount.restore_deferred type={}", ex.getClass().getSimpleName());
        }
    }

    public WinbackDiscountService.Quote efiQuote(Store store, PlanType plan) {
        return winbackDiscounts == null ? new WinbackDiscountService.Quote(null, efi.amount(plan), efi.amount(plan))
                : winbackDiscounts.quote(store, plan);
    }

    private void activate(PaymentSubscription local, Store store, String source) {
        PlanType previous = store.getPlan();
        local.setAccessActive(true);
        store.setBillingSuspended(false);
        // A confirmed paid subscription replaces any active courtesy immediately.
        // Pending or failed checkouts leave the courtesy untouched.
        store.setCourtesyPremium(false);
        store.setCourtesyPremiumReason(null);
        store.setPremiumBonusExpiresAt(null);
        store.setPremiumBonusPlan(null);
        if (previous != local.getPlan()) {
            store.setPlan(local.getPlan());
            event(store.getStoreId(), previous, local.getPlan(), source);
        }
    }

    private void deactivate(PaymentSubscription local, Store store, String source) {
        local.setAccessActive(false);
        if (store != null && store.getPlan().isBillable() && !store.isCourtesyPremium()) {
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
    private static boolean defaultCheckoutUrlMissing(String value) {
        return value != null && value.contains("transaction_default_checkout_url_not_set");
    }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static boolean canceled(String status) {
        return "cancelled".equalsIgnoreCase(status) || "canceled".equalsIgnoreCase(status);
    }
    private BigDecimal amount(PaymentGateway gateway, Store store, PlanType plan) {
        PaymentProviderType provider = gateway.provider();
        return (provider != null && provider.isStripeOrCreem())
                || provider == PaymentProviderType.DODO || provider == PaymentProviderType.DODO_TEST || provider == PaymentProviderType.DLOCAL_GO
                ? gateway.amount(store, plan) : gateway.amount(plan);
    }
    private int graceDays(PaymentProviderType provider) {
        if (provider == PaymentProviderType.DLOCAL_GO) return 0;
        if (provider == PaymentProviderType.STRIPE) return ((br.com.nuvemcustomfields.payment.StripeGateway) router.require(provider)).graceDays();
        if (provider == PaymentProviderType.CREEM) return ((br.com.nuvemcustomfields.payment.CreemGateway) router.require(provider)).graceDays();
        return mercadoPagoProperties.safeGraceDays();
    }
    private boolean reusableCheckout(PaymentSubscription subscription) {
        return true;
    }
}
