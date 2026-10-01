package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.WinbackCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WinbackCouponRepository extends JpaRepository<WinbackCoupon, String> {
    Optional<WinbackCoupon> findByStoreId(Long storeId);
    Optional<WinbackCoupon> findByCampaignId(String campaignId);
}
