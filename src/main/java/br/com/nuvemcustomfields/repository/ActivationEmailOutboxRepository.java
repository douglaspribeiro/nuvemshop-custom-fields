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
    Optional<ActivationEmailOutbox> findByUnsubscribeTokenHash(String hash);
    List<ActivationEmailOutbox> findByStoreIdAndState(Long storeId, ActivationEmailOutbox.State state);
    Optional<ActivationEmailOutbox> findByStoreIdAndInstalledAtAndStep(Long storeId, Instant installedAt, String step);
    boolean existsByStoreIdAndStepAndStateIn(Long storeId, String step, java.util.Collection<ActivationEmailOutbox.State> states);
    @Query("select e.step, e.state, count(e) from ActivationEmailOutbox e group by e.step, e.state")
    List<Object[]> countsByStepAndState();
    @Query(value = """
            select outcome, count(*) from (
                select o.id, case
                    when max(case when f.event_type='Complaint' then 1 else 0 end)=1 then 'complaint'
                    when max(case when f.event_type='Bounce' then 1 else 0 end)=1 then 'bounced'
                    when max(case when f.event_type='Delivery' then 1 else 0 end)=1 then 'delivered'
                    else 'awaiting' end as outcome
                from nc39_activation_email_outbox o
                left join nc39_activation_email_feedback f on f.event_id=o.id
                where o.state='SENT' and o.step in ('D3','D7','D11')
                group by o.id
            ) delivery_status group by outcome
            """, nativeQuery = true)
    List<Object[]> countsByDeliveryOutcome();
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
