package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PlanAsset;
import br.com.nuvemcustomfields.entity.CommercePlatform;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanLimitServiceTest {

    private final PlanCatalogService planCatalogService = mock(PlanCatalogService.class);
    private final PlanLimitService service = new PlanLimitService(
            mock(PersonalizationRuleRepository.class),
            mock(PersonalizationFieldRepository.class),
            planCatalogService
    );

    @Test
    void exposesCommercialLimitsByPlan() {
        when(planCatalogService.activePlan(PlanType.FREE)).thenReturn(plan(PlanType.FREE, 1, 1));
        when(planCatalogService.activePlan(PlanType.PREMIUM)).thenReturn(plan(PlanType.PREMIUM, 10, 3));
        when(planCatalogService.activePlan(PlanType.PREMIUM_PLUS)).thenReturn(plan(PlanType.PREMIUM_PLUS, -1, -1));

        assertThat(service.productLimit(PlanType.FREE)).isEqualTo(1);
        assertThat(service.fieldLimit(PlanType.FREE)).isEqualTo(1);
        assertThat(service.productLimit(PlanType.PREMIUM)).isEqualTo(10);
        assertThat(service.fieldLimit(PlanType.PREMIUM)).isEqualTo(3);
        assertThat(service.productLimit(PlanType.PREMIUM_PLUS)).isEqualTo(-1);
        assertThat(service.fieldLimit(PlanType.PREMIUM_PLUS)).isEqualTo(-1);
    }

    @Test
    void suspendedBillingUsesFreeLimitsAsEffectivePlan() {
        PersonalizationRuleRepository ruleRepository = mock(PersonalizationRuleRepository.class);
        PlanCatalogService catalogService = mock(PlanCatalogService.class);
        when(catalogService.activePlan(PlanType.FREE)).thenReturn(plan(PlanType.FREE, 1, 1));
        PlanLimitService suspendedService = new PlanLimitService(
                ruleRepository,
                mock(PersonalizationFieldRepository.class),
                catalogService
        );
        Store store = new Store();
        store.setStoreId(123L);
        store.setPlan(PlanType.PREMIUM_PLUS);
        store.setBillingSuspended(true);
        when(ruleRepository.countByPlatformAndStoreId(CommercePlatform.NUVEMSHOP, 123L)).thenReturn(1L);

        assertThat(suspendedService.usage(store, 0).plan()).isEqualTo(PlanType.FREE);
        assertThat(suspendedService.canAddProduct(store)).isFalse();
    }

    @Test
    void canEvaluateShopifyUsageWithSeparatePlatformKey() {
        PersonalizationRuleRepository ruleRepository = mock(PersonalizationRuleRepository.class);
        PlanCatalogService catalogService = mock(PlanCatalogService.class);
        when(catalogService.activePlan(PlanType.PREMIUM)).thenReturn(plan(PlanType.PREMIUM, 10, 3));
        when(ruleRepository.countByPlatformAndStoreId(CommercePlatform.SHOPIFY, 55L)).thenReturn(2L);
        PlanLimitService shopifyService = new PlanLimitService(
                ruleRepository,
                mock(PersonalizationFieldRepository.class),
                catalogService
        );

        var usage = shopifyService.usage(CommercePlatform.SHOPIFY, 55L, PlanType.PREMIUM, 1);

        assertThat(usage.productsUsed()).isEqualTo(2);
        assertThat(usage.productLimit()).isEqualTo(10);
        assertThat(shopifyService.canAddProduct(CommercePlatform.SHOPIFY, 55L, PlanType.PREMIUM)).isTrue();
    }

    private PlanAsset plan(PlanType type, long productLimit, long fieldLimit) {
        PlanAsset plan = new PlanAsset();
        plan.setPlanType(type);
        plan.setProductLimit(productLimit);
        plan.setFieldLimit(fieldLimit);
        return plan;
    }
}
