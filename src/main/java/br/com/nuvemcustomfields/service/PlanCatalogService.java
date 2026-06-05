package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PlanAsset;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.repository.PlanAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PlanCatalogService {

    private final PlanAssetRepository planAssetRepository;

    public PlanCatalogService(PlanAssetRepository planAssetRepository) {
        this.planAssetRepository = planAssetRepository;
    }

    public PlanAsset activePlan(PlanType planType) {
        LocalDate today = LocalDate.now();
        return planAssetRepository.findActiveByPlanTypeOnDate(planType, today).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Plano ativo nao encontrado para " + planType + "."));
    }

    public Map<PlanType, PlanAsset> activePlansByType() {
        return Arrays.stream(PlanType.values())
                .collect(Collectors.toMap(type -> type, this::activePlan));
    }

    public List<PlanAsset> paidActivePlans() {
        return Arrays.stream(PlanType.values())
                .filter(type -> type != PlanType.FREE)
                .map(this::activePlan)
                .toList();
    }

    public List<PlanAsset> allVersions() {
        return planAssetRepository.findByActiveTrueOrderByPlanTypeAscEffectiveFromDesc();
    }

    public Optional<PlanType> planTypeForBillingExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now();
        var current = planAssetRepository.findActiveByBillingExternalIdOnDate(externalId, today).stream()
                .findFirst()
                .map(PlanAsset::getPlanType);
        if (current.isPresent()) {
            return current;
        }
        return planAssetRepository.findByBillingExternalIdAndActiveTrueOrderByEffectiveFromDesc(externalId).stream()
                .findFirst()
                .map(PlanAsset::getPlanType);
    }

    @Transactional
    public PlanAsset createVersion(
            PlanType planType,
            String displayName,
            String description,
            String billingExternalId,
            String currency,
            BigDecimal amount,
            long productLimit,
            long fieldLimit,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil
    ) {
        validate(planType, displayName, billingExternalId, currency, amount, productLimit, fieldLimit, effectiveFrom, effectiveUntil);
        closeOverlappingCurrentVersion(planType, effectiveFrom);
        var overlaps = planAssetRepository.findOverlappingActiveVersions(planType, effectiveFrom, effectiveUntil);
        if (!overlaps.isEmpty()) {
            throw new IllegalArgumentException("Ja existe uma versao de plano ativa nesse periodo.");
        }

        PlanAsset asset = new PlanAsset();
        asset.setPlanType(planType);
        asset.setDisplayName(displayName.strip());
        asset.setDescription(description == null || description.isBlank() ? null : description.strip());
        asset.setBillingExternalId(normalizeBillingExternalId(planType, billingExternalId));
        asset.setCurrency(currency.strip().toUpperCase());
        asset.setAmount(amount);
        asset.setProductLimit(productLimit);
        asset.setFieldLimit(fieldLimit);
        asset.setEffectiveFrom(effectiveFrom);
        asset.setEffectiveUntil(effectiveUntil);
        asset.setActive(true);
        return planAssetRepository.save(asset);
    }

    private void closeOverlappingCurrentVersion(PlanType planType, LocalDate effectiveFrom) {
        LocalDate previousDay = effectiveFrom.minusDays(1);
        planAssetRepository.findActiveByPlanTypeOnDate(planType, effectiveFrom).stream()
                .filter(asset -> asset.getEffectiveFrom().isBefore(effectiveFrom))
                .max(Comparator.comparing(PlanAsset::getEffectiveFrom))
                .ifPresent(asset -> {
                    asset.setEffectiveUntil(previousDay);
                    planAssetRepository.save(asset);
                });
    }

    private void validate(
            PlanType planType,
            String displayName,
            String billingExternalId,
            String currency,
            BigDecimal amount,
            long productLimit,
            long fieldLimit,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil
    ) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Informe o nome do plano.");
        }
        if (currency == null || currency.isBlank() || currency.strip().length() != 3) {
            throw new IllegalArgumentException("Informe uma moeda valida com 3 letras.");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Informe um valor maior ou igual a zero.");
        }
        if (productLimit < -1 || fieldLimit < -1) {
            throw new IllegalArgumentException("Limites devem ser -1 para ilimitado ou maior que zero.");
        }
        if (productLimit == 0 || fieldLimit == 0) {
            throw new IllegalArgumentException("Limites devem ser -1 para ilimitado ou maior que zero.");
        }
        if (effectiveFrom == null || effectiveFrom.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("A vigencia deve comecar hoje ou em uma data futura.");
        }
        if (effectiveUntil != null && effectiveUntil.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("A data final deve ser posterior ao inicio da vigencia.");
        }
        if (planType != PlanType.FREE && (billingExternalId == null || billingExternalId.isBlank())) {
            throw new IllegalArgumentException("Informe o ID externo de billing para planos pagos.");
        }
    }

    private String normalizeBillingExternalId(PlanType planType, String billingExternalId) {
        if (planType == PlanType.FREE || billingExternalId == null || billingExternalId.isBlank()) {
            return null;
        }
        return billingExternalId.strip();
    }
}
