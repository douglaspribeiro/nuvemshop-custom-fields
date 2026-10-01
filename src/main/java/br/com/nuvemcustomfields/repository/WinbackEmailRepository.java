package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.WinbackEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WinbackEmailRepository extends JpaRepository<WinbackEmail, String> {
    List<WinbackEmail> findByStatusOrderByCreatedAtAsc(String status, org.springframework.data.domain.Pageable page);
    List<WinbackEmail> findByCampaignIdOrderByCreatedAtAsc(String campaignId);
    Optional<WinbackEmail> findByCampaignIdAndStep(String campaignId, String step);
    @org.springframework.data.jpa.repository.Query("select e.campaignId from WinbackEmail e where e.id = :id")
    Optional<String> campaignIdFor(String id);
}
