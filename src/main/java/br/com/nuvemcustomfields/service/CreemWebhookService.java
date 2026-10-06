package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.CreemGateway;
import br.com.nuvemcustomfields.repository.PaymentWebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

@Service
public class CreemWebhookService {
    private final CreemGateway gateway;
    private final PaymentWebhookEventRepository events;
    private final PaymentSubscriptionService subscriptions;
    private final ObjectMapper mapper;
    public CreemWebhookService(CreemGateway gateway, PaymentWebhookEventRepository events,
                               PaymentSubscriptionService subscriptions, ObjectMapper mapper) {
        this.gateway = gateway; this.events = events; this.subscriptions = subscriptions; this.mapper = mapper;
    }
    public void receive(String body, String signature) {
        var notification = gateway.verifyNotification(body, signature, null, null);
        if (events.findByEventKey(notification.eventKey()).isPresent()) return;
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setProvider(notification.provider()); event.setProviderEnvironment(notification.environment());
        event.setEventKey(notification.eventKey()); event.setEventType(notification.type());
        event.setOccurredAt(notification.occurredAt()); event.setProviderResourceId(notification.resourceId());
        event.setPayloadJson(notification.payload());
        try { events.saveAndFlush(event); }
        catch (DataIntegrityViolationException ex) {
            if (events.findByEventKey(notification.eventKey()).isEmpty()) throw ex;
        }
    }
    @Scheduled(fixedDelayString = "${payments.webhook-processing-delay-ms:5000}")
    public void processPending() {
        if (!gateway.operational()) return;
        var due = events.findTop50ByProviderAndProviderEnvironmentAndStatusInAndNextAttemptAtLessThanEqualOrderByReceivedAtAsc(
                PaymentProviderType.CREEM, gateway.environment(), EnumSet.of(PaymentWebhookStatus.RECEIVED,
                        PaymentWebhookStatus.FAILED, PaymentWebhookStatus.PROCESSING), Instant.now());
        for (PaymentWebhookEvent event : due) {
            Instant now = Instant.now();
            if (events.claimCreem(event.getId(), now, now.plus(5, ChronoUnit.MINUTES)) != 1) continue;
            event.setProcessingAttempts(event.getProcessingAttempts() + 1);
            try {
                PaymentSubscription subscription = dispatch(event);
                event.setStatus(subscription == null ? PaymentWebhookStatus.IGNORED : PaymentWebhookStatus.PROCESSED);
                if (subscription != null) event.setStoreId(subscription.getStoreId());
                event.setProcessedAt(Instant.now()); event.setLastError(null);
            } catch (Exception ex) {
                event.setStatus(PaymentWebhookStatus.FAILED);
                event.setLastError("Não foi possível conciliar o evento Creem. Nova tentativa agendada.");
                event.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600, 1L << Math.min(11, event.getProcessingAttempts()))));
            }
            events.saveAndFlush(event);
        }
    }
    private PaymentSubscription dispatch(PaymentWebhookEvent event) throws Exception {
        // Sempre consultar o estado remoto atual para não reverter estado com eventos atrasados.
        return switch (event.getEventType()) {
            case "checkout.completed" -> subscriptions.synchronizeCreemCheckout(event.getProviderResourceId());
            case "subscription.paid" -> subscriptions.synchronizeFromSubscription(PaymentProviderType.CREEM, event.getProviderResourceId());
            case "subscription.active", "subscription.update", "subscription.scheduled_cancel",
                 "subscription.canceled", "subscription.past_due", "subscription.unpaid", "subscription.expired",
                 "subscription.trialing", "subscription.paused" -> subscriptions.synchronizeFromSubscriptionWithoutPayment(
                    PaymentProviderType.CREEM, event.getProviderResourceId());
            case "refund.created", "dispute.created" -> {
                var object = mapper.readTree(event.getPayloadJson()).path("object");
                String subscriptionId = CreemGateway.resourceId(object.path("subscription"));
                if (subscriptionId == null) {
                    var transaction = object.path("transaction");
                    if (transaction.isTextual()) transaction = gateway.getTransaction(transaction.asText());
                    subscriptionId = CreemGateway.resourceId(transaction.path("subscription"));
                }
                yield subscriptionId == null ? null : subscriptions.synchronizeFromSubscription(PaymentProviderType.CREEM, subscriptionId);
            }
            default -> null;
        };
    }
}
