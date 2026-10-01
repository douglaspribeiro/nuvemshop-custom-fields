package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WinbackDiscountRestorationJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(WinbackDiscountRestorationJob.class);
    private final PaymentSubscriptionRepository subscriptions;
    private final WinbackDiscountService discounts;
    public WinbackDiscountRestorationJob(PaymentSubscriptionRepository subscriptions, WinbackDiscountService discounts) {
        this.subscriptions = subscriptions; this.discounts = discounts;
    }
    @Scheduled(fixedDelayString = "${winback.discount.restore-delay-ms:60000}")
    public void retry() {
        // Pending financial work must continue even when new promotional issuance is disabled.
        for (var subscription : subscriptions.findByWinbackRestorePendingTrue()) {
            try { discounts.retryRestore(subscription.getStoreId()); }
            catch (RuntimeException ex) {
                LOGGER.warn("winback.discount.restore_failed type={}", ex.getClass().getSimpleName());
            }
        }
    }
}
