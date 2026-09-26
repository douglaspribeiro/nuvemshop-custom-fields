package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentSubscriptionStatus;
import br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class PaymentReconciliationJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentReconciliationJob.class);
    private final PaymentSubscriptionRepository repository;
    private final PaymentSubscriptionService service;
    private final PaymentGatewayRouter router;
    private final PaymentWebhookService webhooks;

    public PaymentReconciliationJob(PaymentSubscriptionRepository repository,
                                    PaymentSubscriptionService service,
                                    PaymentGatewayRouter router,
                                    PaymentWebhookService webhooks) {
        this.repository = repository;
        this.service = service;
        this.router = router;
        this.webhooks = webhooks;
    }

    @Scheduled(fixedDelayString = "${payments.reconciliation-delay-ms:3600000}")
    public void reconcile() {
        var statuses = EnumSet.of(PaymentSubscriptionStatus.PENDING, PaymentSubscriptionStatus.ACTIVE,
                PaymentSubscriptionStatus.PAST_DUE, PaymentSubscriptionStatus.PAUSED,
                PaymentSubscriptionStatus.CANCELED);
        for (PaymentProviderType provider : PaymentProviderType.values()) {
            if (!router.configured(provider)) continue;
            for (var subscription : repository.findByProviderAndStatusIn(provider, statuses)) {
                try {
                    if (subscription.getProviderSubscriptionId() != null) service.reconcile(subscription.getStoreId());
                } catch (RuntimeException ex) {
                    subscription.setLastError(ex.getMessage() == null ? null
                            : ex.getMessage().substring(0, Math.min(500, ex.getMessage().length())));
                    repository.save(subscription);
                    LOGGER.warn("payments.reconciliation.failed store_id={} message={}",
                            subscription.getStoreId(), ex.getMessage());
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "${payments.webhook-processing-delay-ms:5000}")
    public void processPaddleWebhooks() {
        if (router.configured(PaymentProviderType.PADDLE)) webhooks.processPendingPaddle();
    }

    @Scheduled(fixedDelayString = "${payments.access-expiration-delay-ms:60000}")
    public void expireCanceledAccess() {
        service.expireCanceledAccess();
    }

    @Scheduled(fixedDelayString = "${payments.pending-expiration-delay-ms:60000}")
    public void expirePendingEfiPayments() {
        var expired = repository.findByProviderAndStatusAndAccessActiveFalseAndPendingStartedAtLessThanEqual(
                PaymentProviderType.EFI, PaymentSubscriptionStatus.PENDING,
                Instant.now().minus(30, ChronoUnit.MINUTES));
        for (var subscription : expired) {
            try {
                service.expirePendingEfi(subscription.getStoreId());
            } catch (RuntimeException ex) {
                LOGGER.warn("payments.efi.pending_expiration_failed store_id={} type={}",
                        subscription.getStoreId(), ex.getClass().getSimpleName());
            }
        }
    }
}
