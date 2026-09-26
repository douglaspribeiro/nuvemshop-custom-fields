package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {
    Optional<PaymentWebhookEvent> findByEventKey(String eventKey);
    void deleteByStoreId(Long storeId);
    List<PaymentWebhookEvent> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByReceivedAtAsc(
            Collection<br.com.nuvemcustomfields.entity.PaymentWebhookStatus> statuses, Instant due);
    @Modifying
    @Transactional
    @Query("update PaymentWebhookEvent e set e.status = br.com.nuvemcustomfields.entity.PaymentWebhookStatus.PROCESSING, " +
            "e.processingAttempts = e.processingAttempts + 1 where e.id = :id and " +
            "e.status in (br.com.nuvemcustomfields.entity.PaymentWebhookStatus.RECEIVED, " +
            "br.com.nuvemcustomfields.entity.PaymentWebhookStatus.FAILED) and e.nextAttemptAt <= :now")
    int claim(Long id, Instant now);
}
