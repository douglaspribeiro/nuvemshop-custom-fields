package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PlanAsset;
import br.com.nuvemcustomfields.entity.PlanType;

import java.time.LocalDate;
import java.util.List;

public interface PlanAssetRepository {
    List<PlanAsset> findByActiveTrueOrderByPlanTypeAscEffectiveFromDesc();
    List<PlanAsset> findByBillingExternalIdAndActiveTrueOrderByEffectiveFromDesc(String billingExternalId);
    List<PlanAsset> findActiveByPlanTypeOnDate(PlanType planType, LocalDate date);
    List<PlanAsset> findActiveByBillingExternalIdOnDate(String externalId, LocalDate date);
    List<PlanAsset> findOverlappingActiveVersions(PlanType planType, LocalDate effectiveFrom, LocalDate effectiveUntil);
    long count();
    PlanAsset save(PlanAsset asset);
}
