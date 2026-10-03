package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.UpgradeCouponUse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UpgradeCouponUseRepository extends JpaRepository<UpgradeCouponUse,Long> {
    Optional<UpgradeCouponUse> findByAdjustmentId(String id);
    long countByCouponIdAndStoreIdAndStatusNot(Long couponId,Long storeId,UpgradeCouponUse.Status status);
    boolean existsByCouponId(Long id);
    List<UpgradeCouponUse> findByCouponId(Long id);
    List<UpgradeCouponUse> findTop100ByOrderByCreatedAtDesc();
}
