package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class VariantPersonalizationServiceTest {
    private final NuvemshopApiClient api = mock(NuvemshopApiClient.class);
    private final PersonalizationRuleRepository rules = mock(PersonalizationRuleRepository.class);
    private final StorefrontCache cache = mock(StorefrontCache.class);
    private final VariantPersonalizationService service = new VariantPersonalizationService(api, rules, cache);
    private final Store store = new Store();
    private final PersonalizationRule rule = new PersonalizationRule();

    private void configure() {
        store.setStoreId(10L); rule.setStoreId(10L); rule.setProductId(20L);
        when(rules.findByStoreIdAndProductId(10L,20L)).thenReturn(Optional.of(rule));
        when(api.listProductVariants(store,20L)).thenReturn(List.of(new NuvemshopApiClient.ProductVariant(30L,"P / Com personalização","60.00")));
    }

    @Test void persistsStableIdsAndInvalidatesCache() {
        configure(); service.save(store,20L,true,List.of(30L,30L));
        assertThat(rule.isVariantRestricted()).isTrue(); assertThat(rule.getVariantIds()).containsExactly(30L);
        verify(rules).save(rule); verify(cache).invalidateStoreAfterCommit(10L);
    }
    @Test void rejectsForeignRemovedAndEmptySelectionWithoutChangingRule() {
        configure(); rule.setVariantRestricted(true); rule.setVariantIds(List.of(30L));
        for(var ids : List.of(List.<Long>of(),List.of(999L),List.of(30L,999L)))
            assertThatThrownBy(()->service.save(store,20L,true,ids)).isInstanceOf(IllegalArgumentException.class);
        assertThat(rule.getVariantIds()).containsExactly(30L); verify(rules,never()).save(any()); verifyNoInteractions(cache);
    }
    @Test void requiresRuleOwnedByAuthenticatedStore() {
        configure(); when(rules.findByStoreIdAndProductId(10L,21L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.save(store,21L,true,List.of(30L))).isInstanceOf(IllegalArgumentException.class);
        verify(api,never()).listProductVariants(store,21L);
    }
    @Test void catalogFailurePreservesCondition() {
        configure(); rule.setVariantRestricted(true); rule.setVariantIds(List.of(30L));
        when(api.listProductVariants(store,20L)).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(()->service.save(store,20L,false,null)).isInstanceOf(IllegalStateException.class);
        assertThat(rule.isVariantRestricted()).isTrue(); assertThat(rule.getVariantIds()).containsExactly(30L);
        verifyNoInteractions(cache);
    }
    @Test void oldRulesAreUnrestrictedAndConditionCanBeDisabled() {
        configure(); assertThat(rule.isVariantRestricted()).isFalse(); assertThat(rule.getVariantIds()).isEmpty();
        rule.setVariantRestricted(true);rule.setVariantIds(List.of(30L));
        service.save(store,20L,false,null);
        assertThat(rule.isVariantRestricted()).isFalse();assertThat(rule.getVariantIds()).isEmpty();
    }
}
