package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.RenewalFailureEmail;
import br.com.nuvemcustomfields.repository.*;
import java.time.Instant;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static br.com.nuvemcustomfields.entity.RenewalFailureEmail.State.*;

@Service
public class RenewalFailureEmailDispatcher {
    private final RenewalFailureEmailRepository queue;
    private final PaymentSubscriptionRepository subscriptions;
    private final RenewalFailureEmailService emails;
    private final TransactionTemplate tx;
    public RenewalFailureEmailDispatcher(RenewalFailureEmailRepository queue, PaymentSubscriptionRepository subscriptions,
            RenewalFailureEmailService emails, PlatformTransactionManager manager) {
        this.queue=queue; this.subscriptions=subscriptions; this.emails=emails; tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${payments.renewal-email-delay-ms:15000}")
    public void dispatch() {
        for (int n=0; n<10; n++) {
            RenewalFailureEmail row = tx.execute(status -> {
                var due = queue.findDue(PENDING, Instant.now(), PageRequest.of(0,1));
                if (due.isEmpty()) return null;
                var current=due.getFirst(); current.claim(); return queue.saveAndFlush(current);
            });
            if (row == null) return;
            try {
                var sub = subscriptions.findByStoreId(row.getStoreId()).orElse(null);
                if (sub == null || sub.getProvider()!=row.getProvider()
                        || !Objects.equals(sub.getProviderSubscriptionId(), row.getSubscriptionId())
                        || !Objects.equals(sub.getRenewalFailurePaymentId(), row.getPaymentId())) {
                    finish(row.getId(), SKIPPED); continue;
                }
                var notice = emails.preview(row.getStoreId());
                if (!notice.eligible()) {
                    // A missing recipient may be corrected; recovered/canceled payments must not receive stale mail.
                    if (notice.unavailableReason().contains("e-mail")) retry(row); else finish(row.getId(), SKIPPED);
                    continue;
                }
                emails.sendAutomatic(row.getStoreId(), notice.fingerprint());
                finish(row.getId(), SENT);
            } catch (IllegalArgumentException ex) {
                retry(row); // Validation/configuration failed before SMTP.
            } catch (RuntimeException ex) {
                // SMTP can have accepted a message before reporting a timeout. Never blindly resend it.
                finish(row.getId(), REVIEW);
                LoggerFactory.getLogger(getClass()).warn("payments.renewal_email.review store_id={} notice_id={} type={}",
                        row.getStoreId(), row.getId(), ex.getClass().getSimpleName());
            }
        }
    }

    private void finish(Long id, RenewalFailureEmail.State state) {
        tx.executeWithoutResult(status -> queue.findById(id).ifPresent(row -> {row.finish(state); queue.save(row);}));
    }
    private void retry(RenewalFailureEmail row) {
        tx.executeWithoutResult(status -> queue.findById(row.getId()).ifPresent(current -> {
            if (current.getAttempts() >= 3) current.finish(FAILED);
            else {current.setState(PENDING); current.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(5)));}
            queue.save(current);
        }));
    }
}
