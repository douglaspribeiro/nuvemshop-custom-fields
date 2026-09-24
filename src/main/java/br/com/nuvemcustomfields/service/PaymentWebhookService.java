package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentSubscription;
import br.com.nuvemcustomfields.entity.PaymentWebhookEvent;
import br.com.nuvemcustomfields.entity.PaymentWebhookStatus;
import br.com.nuvemcustomfields.payment.GatewayNotification;
import br.com.nuvemcustomfields.repository.PaymentWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PaymentWebhookService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentGatewayRouter router;
    private final PaymentWebhookEventRepository events;
    private final PaymentSubscriptionService subscriptions;

    public PaymentWebhookService(PaymentGatewayRouter router, PaymentWebhookEventRepository events,
                                 PaymentSubscriptionService subscriptions) {
        this.router = router;
        this.events = events;
        this.subscriptions = subscriptions;
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

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
