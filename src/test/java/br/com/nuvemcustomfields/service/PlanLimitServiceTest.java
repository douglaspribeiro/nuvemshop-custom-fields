package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.PlanAsset;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanLimitServiceTest {
    private final PlanCatalogService catalog = mock(PlanCatalogService.class);

    @org.junit.jupiter.api.BeforeEach
    void catalogLimits() {
        long[][] limits = {{1, 1}, {1, 3}, {10, 3}, {50, -1}, {-1, -1}};
        for (var plan : PlanType.values()) {
            var asset = new PlanAsset();
            asset.setProductLimit(limits[plan.ordinal()][0]);
            asset.setFieldLimit(limits[plan.ordinal()][1]);
            when(catalog.activePlan(plan)).thenReturn(asset);
        }
    }

    private final PlanLimitService service = new PlanLimitService(
            mock(PersonalizationRuleRepository.class),
            mock(PersonalizationFieldRepository.class), catalog
    );

    @Test
    void appliesChangedCatalogLimit() {
        catalog.activePlan(PlanType.PREMIUM).setProductLimit(25);
        catalog.activePlan(PlanType.PREMIUM).setFieldLimit(8);
        assertThat(service.productLimit(PlanType.PREMIUM)).isEqualTo(25);
        assertThat(service.fieldLimit(PlanType.PREMIUM)).isEqualTo(8);
    }

    @Test
    void exposesCommercialLimitsByPlan() {
        assertThat(service.productLimit(PlanType.FREE)).isEqualTo(1);
        assertThat(service.fieldLimit(PlanType.FREE)).isEqualTo(1);
        assertThat(service.productLimit(PlanType.FREE_GRATIS)).isEqualTo(1);
        assertThat(service.fieldLimit(PlanType.FREE_GRATIS)).isEqualTo(3);
        assertThat(service.productLimit(PlanType.PREMIUM)).isEqualTo(10);
        assertThat(service.fieldLimit(PlanType.PREMIUM)).isEqualTo(3);
        assertThat(service.productLimit(PlanType.PREMIUM_PLUS)).isEqualTo(50);
        assertThat(service.fieldLimit(PlanType.PREMIUM_PLUS)).isEqualTo(-1);
        assertThat(service.productLimit(PlanType.PREMIUM_ULTRA)).isEqualTo(-1);
        assertThat(service.fieldLimit(PlanType.PREMIUM_ULTRA)).isEqualTo(-1);
    }

    @Test
    void suspendedBillingUsesFreeLimitsAsEffectivePlan() {
        PersonalizationRuleRepository ruleRepository = mock(PersonalizationRuleRepository.class);
        PlanLimitService suspendedService = new PlanLimitService(ruleRepository, mock(PersonalizationFieldRepository.class), catalog);
        Store store = new Store();
        store.setStoreId(123L);
        store.setPlan(PlanType.PREMIUM_PLUS);
        store.setBillingSuspended(true);
        when(ruleRepository.countByStoreId(123L)).thenReturn(1L);

        assertThat(suspendedService.usage(store, 0).plan()).isEqualTo(PlanType.FREE);
        assertThat(suspendedService.canAddProduct(store)).isFalse();
    }

    @Test
    void internalFreePlanKeepsItsLimitsEvenIfBillingIsSuspended() {
        Store store = new Store();
        store.setStoreId(123L);
        store.setPlan(PlanType.FREE_GRATIS);
        store.setBillingSuspended(true);

        assertThat(service.usage(store, 0).plan()).isEqualTo(PlanType.FREE_GRATIS);
        assertThat(service.fieldLimit(store.getEffectivePlan())).isEqualTo(3);
    }
}
