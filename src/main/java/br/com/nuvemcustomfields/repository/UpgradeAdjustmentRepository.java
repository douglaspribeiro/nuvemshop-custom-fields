package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.UpgradeAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface UpgradeAdjustmentRepository extends JpaRepository<UpgradeAdjustment,String> {
    boolean existsByCouponId(Long couponId);
    List<UpgradeAdjustment> findTop100ByStateNotOrderByQuotedAtDesc(UpgradeAdjustment.State state);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UpgradeAdjustment a where a.id = :id")
    Optional<UpgradeAdjustment> lockById(String id);
    List<UpgradeAdjustment> findByStoreIdOrderByQuotedAtDesc(Long storeId);
    List<UpgradeAdjustment> findTop50ByStateInOrderByQuotedAtAsc(Collection<UpgradeAdjustment.State> states);
}
