package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Commit the reservation before HTTP; never hold the store lock during the remote call. */
@Service
public class DlocalGoCheckoutService {
    @org.springframework.beans.factory.annotation.Autowired
    private BuyerUploadOfferService buyerUploadOffer;
    private final StoreRepository stores;
    private final PaymentSubscriptionRepository subscriptions;
    private final PaymentAttemptRepository attempts;
    private final PaymentGatewayRouter router;
    private final DlocalGoGateway gateway;
    private final NuvemshopProperties properties;
    private final TransactionTemplate transaction;
    private org.springframework.beans.factory.ObjectProvider<PaymentSubscriptionService> subscriptionService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSubscriptionService(org.springframework.beans.factory.ObjectProvider<PaymentSubscriptionService> service) {
        this.subscriptionService = service;
    }

    public DlocalGoCheckoutService(StoreRepository stores, PaymentSubscriptionRepository subscriptions,
            PaymentAttemptRepository attempts, PaymentGatewayRouter router, DlocalGoGateway gateway,
            NuvemshopProperties properties, PlatformTransactionManager manager) {
        this.stores = stores; this.subscriptions = subscriptions; this.attempts = attempts;
        this.router = router; this.gateway = gateway; this.properties = properties;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public String start(Long storeId, PlanType plan) {
        if (subscriptionService != null) subscriptionService.getObject().preparePendingCheckout(storeId, plan);
        Reservation reservation = transaction.execute(status -> reserve(storeId, plan));
        if (reservation.checkoutUrl() != null) return reservation.checkoutUrl();
        try {
            GatewayCheckout checkout = gateway.createCheckout(reservation.store(), plan, reservation.reference(), properties.appBaseUrl() + "/admin/billing/return");
            return transaction.execute(status -> {
                PaymentSubscription local = current(storeId, reservation.reference());
                if (local.getProviderCheckoutId() != null && !local.getProviderCheckoutId().equals(checkout.checkoutResourceId()))
                    throw new PaymentGatewayException("A dLocal Go retornou outro checkout para esta tentativa.");
                local.setProviderCheckoutId(checkout.checkoutResourceId()); local.setCheckoutUrl(checkout.checkoutUrl());
                if (!local.isAccessActive()) local.setProviderStatus(checkout.providerStatus());
                subscriptions.saveAndFlush(local);
                PaymentAttempt attempt = attempts.findByExternalReference(reservation.reference()).orElseThrow();
                attempt.setProviderTransactionId(checkout.checkoutResourceId());
                if (attempt.getStatus() != PaymentAttemptStatus.COMPLETED) attempt.setStatus(PaymentAttemptStatus.OPEN);
                attempts.saveAndFlush(attempt);
                return checkout.checkoutUrl();
            });
        } catch (RuntimeException ex) {
            transaction.executeWithoutResult(status -> {
                PaymentSubscription local = current(storeId, reservation.reference());
                if (!local.isAccessActive()) {
                    local.setLastError("O pagamento está aguardando confirmação. Contate o suporte antes de iniciar outra tentativa.");
                    local.setTechnicalError("Não foi possível confirmar a resposta dLocal Go.");
                    subscriptions.saveAndFlush(local);
                }
                PaymentAttempt attempt = attempts.findByExternalReference(reservation.reference()).orElseThrow();
                if (attempt.getStatus() != PaymentAttemptStatus.COMPLETED) {
                    attempt.setStatus(PaymentAttemptStatus.UNKNOWN);
                    attempt.setLastError("Resposta dLocal Go indeterminada; verificar o checkout no gateway.");
                    attempts.saveAndFlush(attempt);
                }
            });
            throw ex;
        }
    }
    private Reservation reserve(Long storeId, PlanType plan) {
        Store store = stores.findActiveByStoreIdForUpdate(storeId).orElseThrow(() -> new IllegalArgumentException("Loja ativa não encontrada."));
        if (router.requireForStore(store).provider() != PaymentProviderType.DLOCAL_GO || !gateway.planAvailable(store, plan))
            throw new IllegalArgumentException("Plano dLocal Go indisponível para esta loja.");
        PaymentSubscription local = subscriptions.findByStoreId(storeId).orElse(null);
        if (local != null && (local.isAccessActive() || local.isWinbackRestorePending()))
            throw new IllegalArgumentException("A assinatura anterior precisa terminar antes de uma nova contratação.");
        if (attempts.findFirstByStoreIdAndStatusInOrderByCreatedAtDesc(storeId,
                EnumSet.of(PaymentAttemptStatus.CREATING, PaymentAttemptStatus.UNKNOWN)).isPresent())
            throw new IllegalArgumentException("A tentativa anterior ainda precisa ser conciliada. Contate o suporte.");
        if (local != null && local.getStatus() == PaymentSubscriptionStatus.PENDING) {
            if (local.getPlan() == plan && local.getProvider() == PaymentProviderType.DLOCAL_GO
                    && local.getProviderEnvironment() == gateway.environment()
                    && local.getCheckoutUrl() != null && !local.getCheckoutUrl().isBlank())
                return new Reservation(store, local.getExternalReference(), local.getCheckoutUrl());
            PendingCheckoutReplacement.record(local, attempts);
        }
        if (local != null && local.getProviderSubscriptionId() != null && local.getStatus() != PaymentSubscriptionStatus.CANCELED)
            throw new IllegalArgumentException("Cancele a assinatura anterior antes de iniciar outra.");
        String reference = "ncf_" + storeId + "_" + UUID.randomUUID().toString().replace("-", "");
        if (local == null) { local = new PaymentSubscription(); local.setStoreId(storeId); if(buyerUploadOffer!=null) buyerUploadOffer.captureNewSubscription(local,store); }
        local.setProvider(PaymentProviderType.DLOCAL_GO); local.setProviderEnvironment(gateway.environment());
        local.setProviderSubscriptionId(null); local.setProviderCheckoutId(null); local.setProviderCustomerId(null);
        local.setProviderPriceId(gateway.priceId(store, plan)); local.setPlan(plan);
        local.setExternalReference(reference); local.setCountryCode(store.getStoreCountryCode());
        local.setCurrency(gateway.currency(store)); local.setAmountValue(gateway.amount(store, plan));
        local.setStatus(PaymentSubscriptionStatus.PENDING); local.setProviderStatus("pending");
        local.setPendingStartedAt(Instant.now()); local.setCheckoutUrl(null); local.setPayerEmail(null);
        local.setAccessActive(false); local.setLastPaymentId(null); local.setLastPaymentStatus(null);
        local.setLastError(null); local.setTechnicalError(null); local.setNextPaymentAt(null);
        local.setCurrentPeriodStart(null); local.setCurrentPeriodEnd(null); local.setGraceUntil(null);
        local.setCancellationEffectiveAt(null); local.setCancellationPending(false); local.clearUpgrade();
        local.applyWinbackCoupon(null, null, null); local.captureAnalytics(); local.setAnalyticsFirstPaymentId(null);
        subscriptions.saveAndFlush(local);
        PaymentAttempt attempt = new PaymentAttempt(); attempt.setStoreId(storeId);
        attempt.setProvider(PaymentProviderType.DLOCAL_GO); attempt.setEnvironment(gateway.environment());
        attempt.setCountryCode(store.getStoreCountryCode()); attempt.setPlan(plan); attempt.setCurrency(local.getCurrency());
        attempt.setAmountValue(local.getAmountValue()); attempt.setExternalReference(reference);
        attempt.setCheckoutTokenHash(hash(reference)); attempt.setStatus(PaymentAttemptStatus.CREATING);
        attempt.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES)); attempts.saveAndFlush(attempt);
        return new Reservation(store, reference, null);
    }
    private PaymentSubscription current(Long storeId, String reference) {
        stores.findActiveByStoreIdForUpdate(storeId).orElseThrow();
        PaymentSubscription local = subscriptions.findByExternalReference(reference).orElseThrow();
        if (local.getProvider() != PaymentProviderType.DLOCAL_GO) throw new IllegalArgumentException("Tentativa pertence a outro gateway.");
        return local;
    }
    private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private record Reservation(Store store, String reference, String checkoutUrl) { }
}
