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
import br.com.nuvemcustomfields.payment.EfiGateway;
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
    private final EfiGateway efi;

    public PaymentSubscriptionService(
            StoreRepository stores,
            PaymentSubscriptionRepository subscriptions,
            PlanEventRepository planEvents,
            PaymentGatewayRouter router,
            NuvemshopApiClient nuvemshopApi,
            NuvemshopProperties nuvemshopProperties,
            MercadoPagoProperties mercadoPagoProperties,
            EfiGateway efi
    ) {
        this.stores = stores;
        this.subscriptions = subscriptions;
        this.planEvents = planEvents;
        this.router = router;
        this.nuvemshopApi = nuvemshopApi;
        this.nuvemshopProperties = nuvemshopProperties;
        this.mercadoPagoProperties = mercadoPagoProperties;
        this.efi = efi;
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

    public boolean efiEnabled() { return efi.configured(); }
    public String efiPayeeCode() { return efi.payeeCode(); }
    public boolean efiSandbox() { return efi.sandbox(); }

    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public void payWithEfi(Long storeId, PlanType plan, EfiGateway.EfiPayer payer, String paymentToken) {
        if (plan == null || !plan.isBillable()) throw new IllegalArgumentException("Selecione um plano pago.");
        if (payer == null || !validPayer(payer) || paymentToken == null || !paymentToken.matches("[a-zA-Z0-9]{20,120}")) {
            throw new IllegalArgumentException("Confira os dados do pagador e do cartão.");
        }
        Store store = stores.findActiveByStoreIdForUpdate(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Loja ativa não encontrada."));
        refreshProfileIfNeeded(store);
        if (store.isCourtesyPremium()) throw new IllegalArgumentException("A cortesia ativa precisa terminar antes da assinatura paga.");
        if (!efi.configured() || !efi.supports(store)) throw new IllegalArgumentException("Pagamento Efí indisponível para esta loja.");
        PaymentSubscription local = subscriptions.findByStoreId(storeId).orElse(null);
        if (local != null && local.getStatus() == PaymentSubscriptionStatus.PENDING
                && hasText(local.getProviderSubscriptionId())) {
            if (!router.configured(local.getProvider())) {
                throw new IllegalStateException("Existe uma assinatura pendente no provedor anterior. Contate o suporte antes de tentar outro pagamento.");
            }
            local = reconcile(storeId);
        }
        if (local != null && local.isAccessActive()) throw new IllegalArgumentException("A loja já possui uma assinatura ativa.");
        if (local != null && local.getStatus() == PaymentSubscriptionStatus.PENDING
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
        local.setProvider(PaymentProviderType.EFI);
        local.setProviderSubscriptionId(null);
        local.setProviderCheckoutId(null);
        local.setExternalReference(reference);
        local.setPayerEmail(payer.email());
        local.setPlan(plan);
        local.setCurrency("BRL");
        local.setAmountValue(efi.amount(plan));
        local.setStatus(PaymentSubscriptionStatus.PENDING);
        local.setProviderStatus("new");
        local.setCheckoutUrl(null);
        local.setLastPaymentId(null);
        local.setLastPaymentStatus(null);
        local.setLastError(null);
        subscriptions.saveAndFlush(local);
        try {
            String id = efi.createSubscription(plan, reference,
                    nuvemshopProperties.appBaseUrl() + "/prod/webhooks/efi3");
            local.setProviderSubscriptionId(id);
            local.setProviderCheckoutId(efi.planId(plan));
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
                subscriptions.saveAndFlush(local);
            }
            GatewayInvoice invoice = hasText(chargeId) && hasText(chargeStatus)
                    ? new GatewayInvoice(chargeId, id, chargeId, chargeStatus) : null;
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
        return router.forStore(store).map(gateway -> gateway.amount(plan)).orElse(BigDecimal.ZERO);
    }

    public String currency(Store store) {
        return router.forStore(store).isPresent() ? "BRL" : store.getStoreCurrency();
    }

    public Optional<PaymentSubscription> find(Long storeId) {
        return subscriptions.findByStoreId(storeId);
    }

    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public String startCheckout(Long storeId, PlanType plan) {
        if (plan == null || !plan.isBillable()) {
            throw new IllegalArgumentException("Selecione o plano Essencial ou Pro.");
        }
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
                && hasText(subscription.getProviderCheckoutId())
                && hasText(subscription.getCheckoutUrl())) {
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
        subscription.setProvider(gateway.provider());
        subscription.setPayerEmail(null);
        subscription.setProviderSubscriptionId(null);
        subscription.setProviderCheckoutId(null);
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
            GatewayCheckout checkout = gateway.createCheckout(store, plan, reference, returnUrl);
            subscription.setProviderSubscriptionId(checkout.subscriptionId());
            subscription.setProviderCheckoutId(checkout.checkoutResourceId());
            subscription.setCheckoutUrl(checkout.checkoutUrl());
            subscription.setProviderStatus(checkout.providerStatus());
            subscription.setLastSyncedAt(Instant.now());
            subscriptions.save(subscription);
            LOGGER.info("payments.checkout.created store_id={} provider={} plan={} checkout_id={} subscription_id={}",
                    storeId, gateway.provider(), plan, checkout.checkoutResourceId(), checkout.subscriptionId());
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
        GatewaySubscription remote = gateway.getSubscription(local.getProviderSubscriptionId());
        validateRemote(local, remote);
        if (local.isCancellationPending()) {
            if (!canceled(remote.status())) {
                gateway.cancel(local.getProviderSubscriptionId());
                remote = gateway.getSubscription(local.getProviderSubscriptionId());
                validateRemote(local, remote);
            }
            if (!canceled(remote.status())) {
                throw new PaymentGatewayException("Aguardando a confirmação do cancelamento pelo provedor.");
            }
        }
        Optional<GatewayInvoice> invoice = latestInvoice(gateway, local, remote);
        return synchronize(local, remote, invoice.orElse(null), "PAYMENT_RECONCILE");
    }

    @Transactional
    public PaymentSubscription synchronizeFromSubscription(PaymentProviderType provider, String subscriptionId) {
        PaymentGateway gateway = router.require(provider);
        GatewaySubscription remote = gateway.getSubscription(subscriptionId);
        PaymentSubscription local = locate(remote);
        return synchronize(local, remote, latestInvoice(gateway, local, remote).orElse(null), "PAYMENT_WEBHOOK");
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
            PaymentGateway gateway = router.require(local.getProvider());
            if (hasText(local.getProviderSubscriptionId())) {
                gateway.cancel(local.getProviderSubscriptionId());
            } else if (hasText(local.getProviderCheckoutId())) {
                gateway.cancelCheckout(local.getProviderCheckoutId());
                local.setStatus(PaymentSubscriptionStatus.CANCELED);
                local.setProviderStatus("canceled");
            } else {
                throw new IllegalArgumentException("Assinatura ainda nao foi criada no provedor.");
            }
            local.setCancellationPending(false);
            subscriptions.saveAndFlush(local);
            if (hasText(local.getProviderSubscriptionId())) reconcile(storeId);
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
            if ((!hasText(local.getProviderSubscriptionId()) && !hasText(local.getProviderCheckoutId()))
                    || local.getStatus() == PaymentSubscriptionStatus.CANCELED) return;
            local.setCancellationPending(true);
            try {
                PaymentGateway gateway = router.require(local.getProvider());
                if (hasText(local.getProviderSubscriptionId())) gateway.cancel(local.getProviderSubscriptionId());
                else gateway.cancelCheckout(local.getProviderCheckoutId());
                local.setCancellationPending(false);
                local.setStatus(PaymentSubscriptionStatus.CANCELED);
                local.setProviderStatus("canceled");
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
        if (hasText(remote.checkoutResourceId())) local.setProviderCheckoutId(remote.checkoutResourceId());
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
        if (("authorized".equals(status) || "active".equals(status) || "new_charge".equals(status))
                && invoice != null && invoice.approved()) {
            local.setStatus(PaymentSubscriptionStatus.ACTIVE);
            local.setGraceUntil(null);
            local.setCancellationPending(false);
            activate(local, store, source);
        } else if ("paused".equals(status)) {
            local.setStatus(PaymentSubscriptionStatus.PAUSED);
            deactivate(local, store, source);
        } else if (canceled(status)) {
            local.setStatus(PaymentSubscriptionStatus.CANCELED);
            local.setCancellationPending(false);
            if (local.getNextPaymentAt() == null || !Instant.now().isBefore(local.getNextPaymentAt())) {
                deactivate(local, store, source);
            }
        } else if (local.getProvider() == PaymentProviderType.EFI && !local.isAccessActive()
                && invoice != null && "unpaid".equalsIgnoreCase(invoice.paymentStatus())) {
            local.setStatus(PaymentSubscriptionStatus.ERROR);
        } else if (local.isAccessActive() && invoice != null && !invoice.approved()) {
            local.setStatus(PaymentSubscriptionStatus.PAST_DUE);
            if (local.getGraceUntil() == null) {
                local.setGraceUntil(Instant.now().plus(mercadoPagoProperties.safeGraceDays(), ChronoUnit.DAYS));
            }
            if (!Instant.now().isBefore(local.getGraceUntil())) deactivate(local, store, source);
        } else {
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
        if (hasText(local.getProviderCheckoutId())
                && !Objects.equals(local.getProviderCheckoutId(), remote.checkoutResourceId())) {
            throw new IllegalArgumentException("Plano de checkout da assinatura divergente.");
        }
        if (hasText(remote.externalReference())
                && !Objects.equals(local.getExternalReference(), remote.externalReference())) {
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
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static boolean canceled(String status) {
        return "cancelled".equalsIgnoreCase(status) || "canceled".equalsIgnoreCase(status);
    }
}
