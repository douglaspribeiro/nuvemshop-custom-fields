package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PaymentAttempt;
import br.com.nuvemcustomfields.entity.PaymentAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    Optional<PaymentAttempt> findByCheckoutTokenHash(String checkoutTokenHash);
    Optional<PaymentAttempt> findByExternalReference(String externalReference);
    Optional<PaymentAttempt> findFirstByStoreIdAndStatusInOrderByCreatedAtDesc(Long storeId, Collection<PaymentAttemptStatus> statuses);
}
