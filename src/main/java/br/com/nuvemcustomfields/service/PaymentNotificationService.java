package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentNotificationOutbox;
import br.com.nuvemcustomfields.entity.PaymentSubscription;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.entity.UpgradeAdjustment;
import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.payment.GatewayInvoice;
import br.com.nuvemcustomfields.repository.PaymentNotificationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PaymentNotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentNotificationService.class);
    private final PaymentNotificationOutboxRepository outbox;
    private final DiscordPaymentWebhookClient discord;

    public PaymentNotificationService(PaymentNotificationOutboxRepository outbox,
                                      DiscordPaymentWebhookClient discord) {
        this.outbox = outbox;
        this.discord = discord;
    }

    @Transactional
    public void enqueue(PaymentSubscription subscription, GatewayInvoice invoice) {
        if (invoice == null || !invoice.approved() || invoice.paymentId() == null
                || invoice.paymentId().isBlank()) return;
        if (outbox.existsByProviderAndPaymentId(subscription.getProvider(), invoice.paymentId())) return;
        PaymentNotificationOutbox notification = new PaymentNotificationOutbox();
        notification.setProvider(subscription.getProvider());
        notification.setPaymentId(invoice.paymentId());
        notification.setStoreId(subscription.getStoreId());
        notification.setPlan(subscription.getPlan());
        notification.setCurrency(subscription.getCurrency());
        notification.setAmountValue(subscription.getWinbackInitialAmount() != null
                && invoice.paymentId().equals(subscription.getWinbackFirstPaymentId())
                ? subscription.getWinbackInitialAmount() : subscription.getAmountValue());
        outbox.save(notification);
    }

    /** Evento independente da mensalidade: valor do ajuste e confirmação do novo plano. */
    @Transactional
    public void enqueueUpgrade(Store store, UpgradeAdjustment adjustment) {
        if (adjustment.getState()!=UpgradeAdjustment.State.COMPLETED) return;
        String eventId=adjustment.reference();
        if (outbox.existsByProviderAndPaymentId(PaymentProviderType.EFI,eventId)) return;
        var notification=new PaymentNotificationOutbox();
        notification.setEventType(PaymentNotificationOutbox.EventType.UPGRADE);
        notification.setProvider(PaymentProviderType.EFI);
        notification.setPaymentId(eventId);
        notification.setStoreId(store.getStoreId());
        notification.setStoreName(store.getStoreName());
        notification.setSourcePlan(adjustment.getSourcePlan());
        notification.setPlan(adjustment.getTargetPlan());
        notification.setCurrency("BRL");
        notification.setAmountValue(adjustment.getDueAmount());
        notification.setRecurringAmount(adjustment.getRegularAmount());
        notification.setCouponCode(adjustment.getCouponCode());
        notification.setSubscriptionId(adjustment.getSubscriptionId());
        notification.setChargeId(adjustment.getChargeId());
        outbox.save(notification);
    }

    @Scheduled(fixedDelayString = "${notifications.discord.retry-delay-ms:30000}")
    @Transactional
    public void deliverDue() {
        if (!discord.configured()) return;
        for (PaymentNotificationOutbox notification : outbox.findDue(Instant.now(), PageRequest.of(0, 20))) {
            try {
                discord.send(notification);
                notification.setDeliveredAt(Instant.now());
                LOGGER.info("payments.notification.delivered provider={} payment_id={}",
                        notification.getProvider(), notification.getPaymentId());
            } catch (RuntimeException ex) {
                // Erros HTTP podem incluir a URL com o token do webhook: registre apenas o tipo.
                int attempts = notification.getAttempts() + 1;
                notification.setAttempts(attempts);
                notification.setNextAttemptAt(Instant.now().plusSeconds(
                        Math.min(3600, 30L << Math.min(attempts - 1, 7))));
                LOGGER.warn("payments.notification.failed provider={} payment_id={} attempt={} type={}",
                        notification.getProvider(), notification.getPaymentId(), attempts,
                        ex.getClass().getSimpleName());
            }
        }
    }
}
