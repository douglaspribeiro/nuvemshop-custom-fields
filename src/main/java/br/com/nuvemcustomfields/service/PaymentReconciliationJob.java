package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentSubscriptionStatus;
import br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

@Component
public class PaymentReconciliationJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentReconciliationJob.class);
    private final PaymentSubscriptionRepository repository;
    private final PaymentSubscriptionService service;
    private final PaymentGatewayRouter router;

    public PaymentReconciliationJob(PaymentSubscriptionRepository repository,
                                    PaymentSubscriptionService service,
                                    PaymentGatewayRouter router) {
        this.repository = repository;
        this.service = service;
        this.router = router;
    }

    @Scheduled(fixedDelayString = "${payments.reconciliation-delay-ms:3600000}")
    public void reconcile() {
        var statuses = EnumSet.of(PaymentSubscriptionStatus.PENDING, PaymentSubscriptionStatus.ACTIVE,
                PaymentSubscriptionStatus.PAST_DUE, PaymentSubscriptionStatus.PAUSED,
                PaymentSubscriptionStatus.CANCELED);
        for (PaymentProviderType provider : new PaymentProviderType[]{PaymentProviderType.MERCADO_PAGO, PaymentProviderType.EFI}) {
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
}
