package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentSubscription;
import br.com.nuvemcustomfields.entity.PaymentWebhookEvent;
import br.com.nuvemcustomfields.entity.PaymentWebhookStatus;
import br.com.nuvemcustomfields.payment.GatewayNotification;
import br.com.nuvemcustomfields.payment.EfiGateway;
import com.fasterxml.jackson.databind.JsonNode;
import br.com.nuvemcustomfields.repository.PaymentWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

@Service
public class PaymentWebhookService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentGatewayRouter router;
    private final PaymentWebhookEventRepository events;
    private final PaymentSubscriptionService subscriptions;
    private final EfiGateway efi;

    public PaymentWebhookService(PaymentGatewayRouter router, PaymentWebhookEventRepository events,
                                 PaymentSubscriptionService subscriptions, EfiGateway efi) {
        this.router = router;
        this.events = events;
        this.subscriptions = subscriptions;
        this.efi = efi;
    }

    public void receiveEfi(String token) {
        if (token == null || !token.matches("[a-zA-Z0-9-]{20,120}")) {
            throw new IllegalArgumentException("Token de notificação inválido.");
        }
        JsonNode history = efi.notification(token);
        if (!history.isArray() || history.isEmpty()) throw new IllegalArgumentException("Notificação Efí vazia.");
        JsonNode latest = history.get(history.size() - 1);
        String subscriptionId = latest.path("identifiers").path("subscription_id").asText("");
        if (subscriptionId.isBlank()) return;
        String key = "EFI:" + token + ":" + latest.path("id").asText();
        PaymentWebhookEvent event = events.findByEventKey(key).orElseGet(() -> {
            PaymentWebhookEvent created = new PaymentWebhookEvent();
            created.setProvider(PaymentProviderType.EFI);
            created.setEventKey(key);
            created.setEventType(latest.path("type").asText("unknown"));
            created.setProviderResourceId(subscriptionId);
            return events.save(created);
        });
        if (event.getStatus() == PaymentWebhookStatus.PROCESSED) return;
        event.setProcessingAttempts(event.getProcessingAttempts() + 1);
        events.save(event);
        try {
            PaymentSubscription subscription = subscriptions.synchronizeFromSubscription(PaymentProviderType.EFI, subscriptionId);
            event.setStoreId(subscription.getStoreId());
            event.setStatus(PaymentWebhookStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            event.setLastError(null);
            events.save(event);
        } catch (RuntimeException ex) {
            event.setStatus(PaymentWebhookStatus.FAILED);
            event.setLastError(truncate(ex.getMessage()));
            events.save(event);
            throw ex;
        }
    }

    public void receiveMercadoPago(String body, String signature, String requestId, String dataId) {
        GatewayNotification notification = router.require(PaymentProviderType.MERCADO_PAGO)
                .verifyNotification(body, signature, requestId, dataId);
        PaymentWebhookEvent event = events.findByEventKey(notification.eventKey()).orElseGet(() -> {
            PaymentWebhookEvent created = new PaymentWebhookEvent();
            created.setProvider(notification.provider());
            created.setEventKey(notification.eventKey());
            created.setEventType(notification.type() == null ? "unknown" : notification.type());
            created.setProviderResourceId(notification.resourceId());
            return events.save(created);
        });
        if (event.getStatus() == PaymentWebhookStatus.PROCESSED || event.getStatus() == PaymentWebhookStatus.IGNORED) return;

        event.setProcessingAttempts(event.getProcessingAttempts() + 1);
        events.save(event);
        try {
            PaymentSubscription subscription = switch (event.getEventType()) {
                case "subscription_preapproval" -> subscriptions.synchronizeFromSubscription(
                        notification.provider(), notification.resourceId());
                case "subscription_authorized_payment" -> subscriptions.synchronizeFromInvoice(
                        notification.provider(), notification.resourceId());
                default -> null;
            };
            if (subscription == null) {
                event.setStatus(PaymentWebhookStatus.IGNORED);
            } else {
                event.setStoreId(subscription.getStoreId());
                event.setStatus(PaymentWebhookStatus.PROCESSED);
            }
            event.setProcessedAt(Instant.now());
            event.setLastError(null);
            events.save(event);
        } catch (RuntimeException ex) {
            event.setStatus(PaymentWebhookStatus.FAILED);
            event.setLastError(truncate(ex.getMessage()));
            events.save(event);
            LOGGER.error("payments.webhook.failed provider={} type={} resource_id={} message={}",
                    notification.provider(), notification.type(), notification.resourceId(), ex.getMessage());
            throw ex;
        }
    }

    public void receivePaddle(String body, String signature) {
        GatewayNotification notification = router.require(PaymentProviderType.PADDLE)
                .verifyNotification(body, signature, null, null);
        PaymentWebhookEvent event = events.findByEventKey(notification.eventKey()).orElseGet(() -> {
            PaymentWebhookEvent created = new PaymentWebhookEvent();
            created.setProvider(notification.provider());
            created.setProviderEnvironment(notification.environment());
            created.setEventKey(notification.eventKey());
            created.setNotificationId(notification.notificationId());
            created.setEventType(notification.type());
            created.setOccurredAt(notification.occurredAt());
            created.setProviderResourceId(notification.resourceId());
            created.setPayloadJson(notification.payload());
            return events.save(created);
        });
        LOGGER.info("payments.webhook.received provider=PADDLE event_key={} status={}", event.getEventKey(), event.getStatus());
    }

    public void processPendingPaddle() {
        var due = events.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByReceivedAtAsc(
                EnumSet.of(PaymentWebhookStatus.RECEIVED, PaymentWebhookStatus.FAILED), Instant.now());
        for (PaymentWebhookEvent event : due) processPaddle(event);
    }

    private void processPaddle(PaymentWebhookEvent event) {
        if (event.getStatus() == PaymentWebhookStatus.PROCESSED || event.getStatus() == PaymentWebhookStatus.IGNORED) return;
        if (events.claim(event.getId(), Instant.now()) != 1) return;
        event.setStatus(PaymentWebhookStatus.PROCESSING);
        event.setProcessingAttempts(event.getProcessingAttempts() + 1);
        try {
            PaymentSubscription subscription = switch (event.getEventType()) {
                case "transaction.completed", "transaction.payment_failed", "transaction.past_due", "transaction.canceled" ->
                        subscriptions.synchronizeFromPaddleTransaction(event.getProviderResourceId());
                case "subscription.created", "subscription.updated", "subscription.activated", "subscription.past_due",
                     "subscription.paused", "subscription.resumed", "subscription.canceled" ->
                        subscriptions.synchronizeFromSubscriptionWithoutPayment(PaymentProviderType.PADDLE, event.getProviderResourceId());
                default -> null;
            };
            event.setStatus(subscription == null ? PaymentWebhookStatus.IGNORED : PaymentWebhookStatus.PROCESSED);
            if (subscription != null) event.setStoreId(subscription.getStoreId());
            event.setProcessedAt(Instant.now());
            event.setLastError(null);
        } catch (RuntimeException ex) {
            event.setStatus(PaymentWebhookStatus.FAILED);
            event.setLastError(truncate(ex.getMessage()));
            long delay = Math.min(3600, 1L << Math.min(11, event.getProcessingAttempts()));
            event.setNextAttemptAt(Instant.now().plus(delay, ChronoUnit.SECONDS));
            LOGGER.warn("payments.webhook.deferred provider=PADDLE event_key={} attempt={} type={}",
                    event.getEventKey(), event.getProcessingAttempts(), ex.getClass().getSimpleName());
        }
        events.save(event);
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
