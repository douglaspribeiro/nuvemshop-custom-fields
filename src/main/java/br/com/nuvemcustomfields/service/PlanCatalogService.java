package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PlanAsset;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.repository.PlanAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
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

    public LocalDate today() {
        return LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
    }

    public PlanAsset activePlan(PlanType planType) {
        LocalDate today = today();
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
                .filter(type -> type.isBillable())
                .map(this::activePlan)
                .toList();
    }

    public List<PlanAsset> allVersions() {
        return planAssetRepository.findAllByOrderByPlanTypeAscEffectiveFromDesc();
    }

    public Optional<PlanType> planTypeForBillingExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        LocalDate today = today();
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
        if (description != null && description.length() > 500) throw new IllegalArgumentException("Descrição deve ter até 500 caracteres.");
        if (billingExternalId != null && billingExternalId.length() > 80) throw new IllegalArgumentException("ID externo deve ter até 80 caracteres.");
        validate(planType, displayName, billingExternalId, currency, amount, productLimit, fieldLimit, effectiveFrom, effectiveUntil);
        var versions = planAssetRepository.lockVersions(planType);
        if (versions.stream().anyMatch(p -> p.isActive() && p.getEffectiveFrom().isAfter(effectiveFrom))) {
            throw new IllegalArgumentException("Já existe uma versão agendada após essa data. Crie a próxima versão após a última vigência.");
        }
        versions.stream().filter(p -> p.isEffectiveOn(effectiveFrom)).forEach(p -> {
            if (p.getEffectiveFrom().equals(effectiveFrom)) p.setActive(false);
            else p.setEffectiveUntil(effectiveFrom.minusDays(1));
            planAssetRepository.saveAndFlush(p);
        });
        var overlaps = planAssetRepository.findOverlappingActiveVersions(planType, effectiveFrom, effectiveUntil);
        if (!overlaps.isEmpty()) {
            throw new IllegalArgumentException("Ja existe uma versao de plano ativa nesse periodo.");
        }

        PlanAsset asset = new PlanAsset();
        asset.setPlanType(planType);
        asset.setDisplayName(displayName.strip());
        asset.setDescription(description == null || description.isBlank() ? null : description.strip());
        asset.setBillingExternalId(normalizeBillingExternalId(planType, billingExternalId));
        asset.setCurrency(currency.strip().toUpperCase(java.util.Locale.ROOT));
        asset.setAmount(amount);
        asset.setProductLimit(productLimit);
        asset.setFieldLimit(fieldLimit);
        asset.setEffectiveFrom(effectiveFrom);
        asset.setEffectiveUntil(effectiveUntil);
        asset.setActive(true);
        return planAssetRepository.save(asset);
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
        if (planType == null) throw new IllegalArgumentException("Informe o plano.");
        if (effectiveUntil != null) throw new IllegalArgumentException("A versão permanece vigente até ser substituída por outra.");
        if (displayName == null || displayName.isBlank() || displayName.length() > 120) {
            throw new IllegalArgumentException("Informe o nome do plano.");
        }
        if (currency == null || currency.isBlank() || !currency.strip().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Informe uma moeda valida com 3 letras.");
        }
        if (amount == null || amount.signum() < 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 8) {
            throw new IllegalArgumentException("Informe um valor maior ou igual a zero.");
        }
        if (productLimit < -1 || fieldLimit < -1) {
            throw new IllegalArgumentException("Limites devem ser -1 para ilimitado ou maior que zero.");
        }
        if (productLimit == 0 || fieldLimit == 0) {
            throw new IllegalArgumentException("Limites devem ser -1 para ilimitado ou maior que zero.");
        }
        if (effectiveFrom == null || effectiveFrom.isBefore(today())) {
            throw new IllegalArgumentException("A vigencia deve comecar hoje ou em uma data futura.");
        }
    }

    private String normalizeBillingExternalId(PlanType planType, String billingExternalId) {
        if (!planType.isBillable() || billingExternalId == null || billingExternalId.isBlank()) {
            return null;
        }
        return billingExternalId.strip();
    }
}
