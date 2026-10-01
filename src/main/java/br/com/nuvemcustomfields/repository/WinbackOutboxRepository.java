package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.WinbackOutbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WinbackOutboxRepository extends JpaRepository<WinbackOutbox, String> {
    boolean existsByStoreIdAndUninstalledAt(Long storeId, Instant uninstalledAt);
    @Query("select w from WinbackOutbox w where w.publishedAt is null and w.nextAttemptAt <= :now order by w.nextAttemptAt")
    List<WinbackOutbox> findDue(Instant now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WinbackOutbox w where w.id = :id")
    Optional<WinbackOutbox> findForUpdate(String id);
}
