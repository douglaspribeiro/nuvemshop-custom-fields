package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.Ga4Outbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface Ga4OutboxRepository extends JpaRepository<Ga4Outbox,Long> {
    boolean existsByEventKey(String eventKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Ga4Outbox e where e.deliveredAt is null and e.expiredAt is null and e.nextAttemptAt <= :now order by e.id")
    List<Ga4Outbox> findDue(@Param("now") Instant now, Pageable pageable);
}
