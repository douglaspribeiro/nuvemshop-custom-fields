package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentAttemptStatus;
import br.com.nuvemcustomfields.repository.PaymentAttemptRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;

@Component
public class ReplacedCheckoutReconciliationJob {
    private final PaymentAttemptRepository attempts;
    private final PaymentSubscriptionService payments;
    private final PaymentGatewayRouter router;

    public ReplacedCheckoutReconciliationJob(PaymentAttemptRepository attempts, PaymentSubscriptionService payments,
                                             PaymentGatewayRouter router) {
        this.attempts = attempts; this.payments = payments; this.router = router;
    }

    @Scheduled(fixedDelayString = "${payments.replaced-checkout-reconcile-delay-ms:60000}")
    public void reconcile() {
        for (var attempt : attempts.findTop50ByStatusOrderByUpdatedAtAsc(PaymentAttemptStatus.REPLACED)) {
            if (!router.operational(attempt.getProvider())) continue;
            try { payments.reconcileReplacedCheckout(attempt.getExternalReference()); }
            catch (RuntimeException ex) {
                LoggerFactory.getLogger(getClass()).warn("payments.replaced_checkout.reconcile_failed store_id={} type={}",
                        attempt.getStoreId(), ex.getClass().getSimpleName());
            }
        }
    }
}
