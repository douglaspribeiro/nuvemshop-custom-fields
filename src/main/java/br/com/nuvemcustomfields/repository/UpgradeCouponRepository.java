package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.UpgradeCoupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UpgradeCouponRepository extends JpaRepository<UpgradeCoupon,Long> {
    Optional<UpgradeCoupon> findByCode(String code);
    List<UpgradeCoupon> findAllByOrderByCreatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from UpgradeCoupon c where c.id=:id")
    Optional<UpgradeCoupon> findLocked(@Param("id") Long id);
}
