package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface RenewalFailureEmailRepository extends JpaRepository<RenewalFailureEmail, Long> {
    boolean existsByProviderAndSubscriptionIdAndPaymentId(PaymentProviderType provider, String subscriptionId, String paymentId);
    java.util.Optional<RenewalFailureEmail> findFirstByStoreIdOrderByIdDesc(Long storeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from RenewalFailureEmail e where e.state = :state and e.nextAttemptAt <= :now order by e.id")
    List<RenewalFailureEmail> findDue(RenewalFailureEmail.State state, Instant now, Pageable pageable);
}
