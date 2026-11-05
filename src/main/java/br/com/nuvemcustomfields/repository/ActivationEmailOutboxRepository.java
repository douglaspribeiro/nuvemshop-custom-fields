package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.ActivationEmailOutbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ActivationEmailOutboxRepository extends JpaRepository<ActivationEmailOutbox, Long> {
    boolean existsByStoreIdAndInstalledAtAndStep(Long storeId, Instant installedAt, String step);
    boolean existsByStoreIdAndStep(Long storeId, String step);
    List<ActivationEmailOutbox> findByStoreIdAndInstalledAtAndState(Long storeId, Instant installedAt, ActivationEmailOutbox.State state);
    @Query("select e from ActivationEmailOutbox e where e.state = 'PENDING' and e.nextAttemptAt <= :now order by e.nextAttemptAt")
    List<ActivationEmailOutbox> findDue(Instant now, Pageable pageable);
    @Query("select e.storeId from ActivationEmailOutbox e where e.id = :id")
    Optional<Long> storeIdFor(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ActivationEmailOutbox e where e.id = :id")
    Optional<ActivationEmailOutbox> findForUpdate(Long id);
}
