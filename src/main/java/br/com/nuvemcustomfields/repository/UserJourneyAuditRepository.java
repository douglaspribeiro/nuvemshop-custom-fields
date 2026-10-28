package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.UserJourneyAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJourneyAuditRepository extends JpaRepository<UserJourneyAudit, Long> {
}
