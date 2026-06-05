package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PlanAsset;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.repository.PlanAssetRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanCatalogServiceTest {

    private final PlanAssetRepository planAssetRepository = mock(PlanAssetRepository.class);
    private final PlanCatalogService service = new PlanCatalogService(planAssetRepository);

    @Test
    void returnsCurrentActivePlan() {
        PlanAsset premium = plan(PlanType.PREMIUM, LocalDate.now().minusDays(1), null);
        when(planAssetRepository.findActiveByPlanTypeOnDate(PlanType.PREMIUM, LocalDate.now())).thenReturn(List.of(premium));

        assertThat(service.activePlan(PlanType.PREMIUM)).isSameAs(premium);
    }

    @Test
    void rejectsMissingCurrentPlan() {
        when(planAssetRepository.findActiveByPlanTypeOnDate(PlanType.PREMIUM, LocalDate.now())).thenReturn(List.of());

        assertThatThrownBy(() -> service.activePlan(PlanType.PREMIUM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Plano ativo");
    }

    @Test
    void creatingImmediateVersionClosesPreviousVersion() {
        LocalDate today = LocalDate.now();
        PlanAsset previous = plan(PlanType.PREMIUM, today.minusMonths(1), null);
        when(planAssetRepository.findActiveByPlanTypeOnDate(PlanType.PREMIUM, today)).thenReturn(List.of(previous));
        when(planAssetRepository.findOverlappingActiveVersions(PlanType.PREMIUM, today, null)).thenReturn(List.of());
        when(planAssetRepository.save(any(PlanAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlanAsset created = service.createVersion(
                PlanType.PREMIUM,
                "Premium reajustado",
                "Plano Premium",
                "PREMIUM",
                "BRL",
                new BigDecimal("12.90"),
                10,
                3,
                today,
                null
        );

        assertThat(previous.getEffectiveUntil()).isEqualTo(today.minusDays(1));
        assertThat(created.getAmount()).isEqualByComparingTo("12.90");
        verify(planAssetRepository).save(previous);
        verify(planAssetRepository).save(created);
    }

    @Test
    void creatingOverlappingVersionIsRejected() {
        LocalDate today = LocalDate.now();
        PlanAsset existing = plan(PlanType.PREMIUM, today.plusDays(5), null);
        when(planAssetRepository.findActiveByPlanTypeOnDate(PlanType.PREMIUM, today)).thenReturn(List.of());
        when(planAssetRepository.findOverlappingActiveVersions(PlanType.PREMIUM, today, null)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createVersion(
                PlanType.PREMIUM,
                "Premium",
                null,
                "PREMIUM",
                "BRL",
                new BigDecimal("9.99"),
                10,
                3,
                today,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodo");
    }

    private PlanAsset plan(PlanType type, LocalDate effectiveFrom, LocalDate effectiveUntil) {
        PlanAsset plan = new PlanAsset();
        plan.setPlanType(type);
        plan.setDisplayName(type.name());
        plan.setBillingExternalId(type == PlanType.FREE ? null : type.name());
        plan.setCurrency("BRL");
        plan.setAmount(BigDecimal.ZERO);
        plan.setProductLimit(1);
        plan.setFieldLimit(1);
        plan.setEffectiveFrom(effectiveFrom);
        plan.setEffectiveUntil(effectiveUntil);
        plan.setActive(true);
        return plan;
    }
}
