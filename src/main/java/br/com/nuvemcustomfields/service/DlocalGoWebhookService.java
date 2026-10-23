package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.DlocalGoGateway;
import br.com.nuvemcustomfields.repository.PaymentWebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.EnumSet;

@Service
public class DlocalGoWebhookService {
    private org.springframework.beans.factory.ObjectProvider<DlocalGoUpgradeService> upgrades;
    private org.springframework.beans.factory.ObjectProvider<DlocalGoRefundService> refunds;
    @org.springframework.beans.factory.annotation.Autowired
    public void setOperations(org.springframework.beans.factory.ObjectProvider<DlocalGoUpgradeService> upgrades,
            org.springframework.beans.factory.ObjectProvider<DlocalGoRefundService> refunds) {this.upgrades=upgrades;this.refunds=refunds;}
    private final DlocalGoGateway gateway;
    private final PaymentWebhookEventRepository events;
    private final PaymentSubscriptionService subscriptions;
    private final br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository locals;
    public DlocalGoWebhookService(DlocalGoGateway gateway, PaymentWebhookEventRepository events, PaymentSubscriptionService subscriptions, br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository locals) {
        this.gateway = gateway; this.events = events; this.subscriptions = subscriptions; this.locals = locals;
    }
    public void receive(String body, String signature, String reference) {
        var notification = gateway.verifyNotification(body, signature, null, reference);
        var local = locals.findByExternalReference(reference).orElseThrow(() -> new IllegalArgumentException("Tentativa desconhecida."));
        if (local.getProvider() != PaymentProviderType.DLOCAL_GO || local.getProviderEnvironment() != gateway.environment())
            throw new IllegalArgumentException("Provedor ou ambiente divergente.");
        if (events.findByEventKey(notification.eventKey()).isPresent()) return;
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setProvider(notification.provider()); event.setProviderEnvironment(notification.environment());
        event.setEventKey(notification.eventKey()); event.setEventType(notification.type());
        event.setOccurredAt(notification.occurredAt()); event.setProviderResourceId(notification.resourceId());
        // Persist only identifiers. dLocal Go events can contain personal and billing details.
        try { events.saveAndFlush(event); }
        catch (DataIntegrityViolationException ex) {
            if (events.findByEventKey(notification.eventKey()).isEmpty()) throw ex;
        }
    }
    @Scheduled(fixedDelayString = "${payments.webhook-processing-delay-ms:5000}")
    public void processPending() {
        if (!gateway.operational()) return;
        var due = events.findTop50ByProviderAndProviderEnvironmentAndStatusInAndNextAttemptAtLessThanEqualOrderByReceivedAtAsc(
                PaymentProviderType.DLOCAL_GO, gateway.environment(), EnumSet.of(PaymentWebhookStatus.RECEIVED,
                        PaymentWebhookStatus.FAILED, PaymentWebhookStatus.PROCESSING), Instant.now());
        for (var event : due) {
            Instant now = Instant.now();
            if (events.claimProvider(event.getId(), PaymentProviderType.DLOCAL_GO, gateway.environment(), now, now.plusSeconds(300)) != 1) continue;
            event.setProcessingAttempts(event.getProcessingAttempts() + 1);
            try {
                PaymentSubscription subscription = dispatch(event);
                event.setStatus(subscription == null ? PaymentWebhookStatus.IGNORED : PaymentWebhookStatus.PROCESSED);
                if (subscription != null) event.setStoreId(subscription.getStoreId());
                event.setProcessedAt(Instant.now()); event.setLastError(null);
            } catch (RuntimeException ex) {
                event.setStatus(PaymentWebhookStatus.FAILED);
                event.setLastError("Não foi possível conciliar o evento dLocal Go. Nova tentativa agendada.");
                event.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600, 1L << Math.min(11, event.getProcessingAttempts()))));
            }
            events.saveAndFlush(event);
        }
    }
    private PaymentSubscription dispatch(PaymentWebhookEvent event) {
        var local = locals.findByExternalReference(event.getProviderResourceId()).orElse(null);
        if (local == null || local.getProvider() != PaymentProviderType.DLOCAL_GO
                || local.getProviderEnvironment() != gateway.environment()) return null;
        // Every delivery fetches authenticated current state; the posted body never grants access.
        if(refunds!=null) refunds.getObject().reconcileStore(local.getStoreId());
        if(upgrades!=null) upgrades.getObject().reconcileStore(local.getStoreId());
        return subscriptions.reconcile(local.getStoreId());
    }
}
