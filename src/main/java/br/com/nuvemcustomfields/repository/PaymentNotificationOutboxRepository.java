package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PaymentNotificationOutbox;
import br.com.nuvemcustomfields.entity.PaymentProviderType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PaymentNotificationOutboxRepository extends JpaRepository<PaymentNotificationOutbox, Long> {
    boolean existsByProviderAndPaymentId(PaymentProviderType provider, String paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from PaymentNotificationOutbox notification "
            + "where notification.deliveredAt is null and notification.nextAttemptAt <= :now "
            + "order by notification.id")
    List<PaymentNotificationOutbox> findDue(@Param("now") Instant now, Pageable pageable);
}
