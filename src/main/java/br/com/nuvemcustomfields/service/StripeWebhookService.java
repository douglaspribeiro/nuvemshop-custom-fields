package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.StripeGateway;
import br.com.nuvemcustomfields.repository.PaymentWebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.EnumSet;

@Service
public class StripeWebhookService {
    private final StripeGateway gateway;
    private final PaymentWebhookEventRepository events;
    private final PaymentSubscriptionService subscriptions;
    public StripeWebhookService(StripeGateway gateway, PaymentWebhookEventRepository events, PaymentSubscriptionService subscriptions) {
        this.gateway = gateway; this.events = events; this.subscriptions = subscriptions;
    }
    public void receive(String body, String signature) {
        var notification = gateway.verifyNotification(body, signature, null, null);
        if (events.findByEventKey(notification.eventKey()).isPresent()) return;
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setProvider(notification.provider()); event.setProviderEnvironment(notification.environment());
        event.setEventKey(notification.eventKey()); event.setEventType(notification.type());
        event.setOccurredAt(notification.occurredAt()); event.setProviderResourceId(notification.resourceId());
        // Persist only identifiers. Stripe events can contain personal and billing details.
        try { events.saveAndFlush(event); }
        catch (DataIntegrityViolationException ex) {
            if (events.findByEventKey(notification.eventKey()).isEmpty()) throw ex;
        }
    }
    @Scheduled(fixedDelayString = "${payments.webhook-processing-delay-ms:5000}")
    public void processPending() {
        if (!gateway.operational()) return;
        var due = events.findTop50ByProviderAndProviderEnvironmentAndStatusInAndNextAttemptAtLessThanEqualOrderByReceivedAtAsc(
                PaymentProviderType.STRIPE, gateway.environment(), EnumSet.of(PaymentWebhookStatus.RECEIVED,
                        PaymentWebhookStatus.FAILED, PaymentWebhookStatus.PROCESSING), Instant.now());
        for (var event : due) {
            Instant now = Instant.now();
            if (events.claimProvider(event.getId(), PaymentProviderType.STRIPE, gateway.environment(), now, now.plusSeconds(300)) != 1) continue;
            event.setProcessingAttempts(event.getProcessingAttempts() + 1);
            try {
                PaymentSubscription subscription = dispatch(event);
                event.setStatus(subscription == null ? PaymentWebhookStatus.IGNORED : PaymentWebhookStatus.PROCESSED);
                if (subscription != null) event.setStoreId(subscription.getStoreId());
                event.setProcessedAt(Instant.now()); event.setLastError(null);
            } catch (RuntimeException ex) {
                event.setStatus(PaymentWebhookStatus.FAILED);
                event.setLastError("Não foi possível conciliar o evento Stripe. Nova tentativa agendada.");
                event.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600, 1L << Math.min(11, event.getProcessingAttempts()))));
            }
            events.saveAndFlush(event);
        }
    }
    private PaymentSubscription dispatch(PaymentWebhookEvent event) {
        // Fetch current state for every notification, including old invoices delivered late.
        return switch (event.getEventType()) {
            case "checkout.session.completed", "checkout.session.expired", "checkout.session.async_payment_succeeded",
                 "checkout.session.async_payment_failed" -> subscriptions.synchronizeStripeCheckout(event.getProviderResourceId());
            case "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted",
                 "customer.subscription.paused", "customer.subscription.resumed" ->
                    subscriptions.synchronizeFromSubscription(PaymentProviderType.STRIPE, event.getProviderResourceId());
            case "invoice.paid", "invoice.payment_succeeded", "invoice.payment_failed", "invoice.voided", "invoice.marked_uncollectible" -> {
                var invoice = gateway.getInvoice(event.getProviderResourceId());
                yield subscriptions.synchronizeFromSubscription(PaymentProviderType.STRIPE, invoice.subscriptionId());
            }
            case "charge.refunded" -> synchronizeCharge(event.getProviderResourceId());
            case "charge.dispute.created", "charge.dispute.updated", "charge.dispute.closed",
                 "charge.dispute.funds_withdrawn", "charge.dispute.funds_reinstated" ->
                    synchronizeCharge(gateway.disputeCharge(event.getProviderResourceId()));
            default -> null;
        };
    }
    private PaymentSubscription synchronizeCharge(String chargeId) {
        PaymentSubscription latest = null;
        for (String id : gateway.invoiceSubscriptionsForCharge(chargeId))
            latest = subscriptions.synchronizeFromSubscription(PaymentProviderType.STRIPE, id);
        return latest;
    }
}
