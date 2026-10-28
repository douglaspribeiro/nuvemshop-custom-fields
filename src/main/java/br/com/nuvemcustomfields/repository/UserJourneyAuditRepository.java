package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.UserJourneyAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJourneyAuditRepository extends JpaRepository<UserJourneyAudit, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select a from UserJourneyAudit a
            where (:storeId is null or a.storeId = :storeId)
              and (:outcome is null or a.outcome = :outcome)
              and (:actor is null or a.actor = :actor)
            order by a.occurredAt desc, a.id desc
            """)
    org.springframework.data.domain.Slice<UserJourneyAudit> search(Long storeId, String outcome, String actor,
            org.springframework.data.domain.Pageable pageable);

}
