package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.WinbackCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WinbackCampaignRepository extends JpaRepository<WinbackCampaign, String> {
    Optional<WinbackCampaign> findByStoreIdAndUninstalledAt(Long storeId, Instant uninstalledAt);
    List<WinbackCampaign> findByStoreIdAndReinstalledAtIsNull(Long storeId);
    boolean existsByStoreIdAndOptedOutAtIsNotNull(Long storeId);
    @Query("select c.storeId from WinbackCampaign c where c.id = :id")
    Optional<Long> storeIdFor(String id);
    @Query("""
        select c from WinbackCampaign c where
        (:query = '' or cast(c.storeId as string) like concat('%', :query, '%')
          or exists (select s.id from Store s where s.storeId = c.storeId
            and lower(s.storeName) like concat('%', :query, '%')))
        and (:fromAt is null or c.uninstalledAt >= :fromAt)
        and (:toAt is null or c.uninstalledAt < :toAt)
        and (:stage = ''
          or (:stage = 'reinstalled' and c.reinstalledAt is not null)
          or (:stage = 'responded' and c.respondedAt is not null)
          or (:stage = 'converted' and c.convertedAt is not null)
          or (:stage = 'paid-before' and c.paidBeforeDeparture = true)
          or (:stage = 'feature-request' and c.reason = 'MISSING_FEATURE')
          or (:stage = 'coupon-used' and exists (select k.code from WinbackCoupon k
            where k.campaignId = c.id and k.usedAt is not null))
          or (:stage = 'opted-out' and c.optedOutAt is not null)
          or (:stage = 'open' and exists (select e.id from WinbackEmailEvent e, WinbackEmail m
            where e.emailId = m.id and m.campaignId = c.id and e.type = 'Open'))
          or (:stage = 'click' and exists (select e.id from WinbackEmailEvent e, WinbackEmail m
            where e.emailId = m.id and m.campaignId = c.id and e.type = 'Click'))
          or (:stage = 'delivery-failed' and exists (select e.id from WinbackEmailEvent e, WinbackEmail m
            where e.emailId = m.id and m.campaignId = c.id and e.type in ('Bounce', 'Reject', 'Rendering Failure'))))
        order by c.uninstalledAt desc, c.id
        """)
    org.springframework.data.domain.Page<WinbackCampaign> search(String query, String stage, Instant fromAt,
            Instant toAt, org.springframework.data.domain.Pageable pageable);
}
