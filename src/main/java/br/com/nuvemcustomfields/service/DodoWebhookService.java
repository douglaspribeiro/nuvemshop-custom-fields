package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.DodoGateway;
import br.com.nuvemcustomfields.repository.PaymentWebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

public abstract class DodoWebhookService {
    private final DodoGateway gateway;
    private final PaymentWebhookEventRepository events;
    private final PaymentSubscriptionService subscriptions;
    public DodoWebhookService(DodoGateway gateway, PaymentWebhookEventRepository events, PaymentSubscriptionService subscriptions) {
        this.gateway = gateway; this.events = events; this.subscriptions = subscriptions;
    }
    public void receive(String body, String signature, String id, String timestamp) {
        var notification = gateway.verifyNotification(body, signature, id, timestamp);
        if (events.findByEventKey(notification.eventKey()).isPresent()) return;
        var event = new PaymentWebhookEvent();
        event.setProvider(notification.provider()); event.setProviderEnvironment(notification.environment());
        event.setEventKey(notification.eventKey()); event.setEventType(notification.type());
        event.setOccurredAt(notification.occurredAt()); event.setProviderResourceId(notification.resourceId());
        event.setPayloadJson(notification.payload());
        try { events.saveAndFlush(event); }
        catch (DataIntegrityViolationException ex) { if (events.findByEventKey(notification.eventKey()).isEmpty()) throw ex; }
    }
    @Scheduled(fixedDelayString = "${payments.webhook-processing-delay-ms:5000}")
    public void processPending() {
        if (!gateway.operational()) return;
        var due = events.findTop50ByProviderAndProviderEnvironmentAndStatusInAndNextAttemptAtLessThanEqualOrderByReceivedAtAsc(
                gateway.provider(), gateway.environment(),
                EnumSet.of(PaymentWebhookStatus.RECEIVED, PaymentWebhookStatus.FAILED, PaymentWebhookStatus.PROCESSING), Instant.now());
        for (var event : due) {
            Instant now = Instant.now();
            if (events.claimProvider(event.getId(), gateway.provider(), gateway.environment(),
                    now, now.plus(5, ChronoUnit.MINUTES)) != 1) continue;
            event.setProcessingAttempts(event.getProcessingAttempts() + 1);
            try {
                PaymentSubscription subscription = switch (event.getEventType()) {
                    case "payment.succeeded", "payment.failed", "payment.cancelled" ->
                            subscriptions.synchronizeFromInvoice(gateway.provider(), event.getProviderResourceId());
                    case "subscription.active", "subscription.renewed", "subscription.updated", "subscription.plan_changed",
                         "subscription.on_hold", "subscription.cancelled", "subscription.failed", "subscription.expired" ->
                            subscriptions.synchronizeFromSubscription(gateway.provider(), event.getProviderResourceId());
                    default -> null;
                };
                event.setStatus(subscription == null ? PaymentWebhookStatus.IGNORED : PaymentWebhookStatus.PROCESSED);
                if (subscription != null) event.setStoreId(subscription.getStoreId());
                event.setProcessedAt(Instant.now()); event.setLastError(null);
            } catch (RuntimeException ex) {
                event.setStatus(PaymentWebhookStatus.FAILED);
                event.setLastError("Não foi possível conciliar o evento Dodo.");
                event.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600, 1L << Math.min(11, event.getProcessingAttempts()))));
            }
            events.saveAndFlush(event);
        }
    }
}
