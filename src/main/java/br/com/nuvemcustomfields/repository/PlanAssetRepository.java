package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PlanAsset;
import br.com.nuvemcustomfields.entity.PlanType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PlanAssetRepository extends JpaRepository<PlanAsset, Long> {

    List<PlanAsset> findAllByOrderByPlanTypeAscEffectiveFromDesc();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PlanAsset p where p.planType = :planType order by p.effectiveFrom desc")
    List<PlanAsset> lockVersions(@Param("planType") PlanType planType);

    List<PlanAsset> findByBillingExternalIdAndActiveTrueOrderByEffectiveFromDesc(String billingExternalId);

    @Query("""
            select p from PlanAsset p
            where p.planType = :planType
              and p.active = true
              and p.effectiveFrom <= :date
              and (p.effectiveUntil is null or p.effectiveUntil >= :date)
            order by p.effectiveFrom desc, p.id desc
            """)
    List<PlanAsset> findActiveByPlanTypeOnDate(@Param("planType") PlanType planType, @Param("date") LocalDate date);

    @Query("""
            select p from PlanAsset p
            where p.billingExternalId = :externalId
              and p.active = true
              and p.effectiveFrom <= :date
              and (p.effectiveUntil is null or p.effectiveUntil >= :date)
            order by p.effectiveFrom desc, p.id desc
            """)
    List<PlanAsset> findActiveByBillingExternalIdOnDate(@Param("externalId") String externalId, @Param("date") LocalDate date);

    @Query("""
            select p from PlanAsset p
            where p.planType = :planType
              and p.active = true
              and (:effectiveUntil is null or p.effectiveFrom <= :effectiveUntil)
              and (p.effectiveUntil is null or p.effectiveUntil >= :effectiveFrom)
            order by p.effectiveFrom desc, p.id desc
            """)
    List<PlanAsset> findOverlappingActiveVersions(
            @Param("planType") PlanType planType,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveUntil") LocalDate effectiveUntil
    );
}
