package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.service.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorefrontEmptyStoreTest {
    @Test void differentProductsShareNegativeStoreStateIncludingRulesWithoutVisibleFields() {
        for (boolean emptyRule : List.of(false, true)) {
            var stores = mock(StoreRepository.class);
            var rules = mock(PersonalizationRuleRepository.class);
            var limits = mock(PlanLimitService.class);
            var store = new Store(); store.setStoreId(1L);
            when(stores.findActiveByStoreId(1L)).thenReturn(Optional.of(store));
            var rule = new PersonalizationRule(); rule.setStoreId(1L);
            when(rules.findByStoreIdAndEnabledTrue(1L)).thenReturn(emptyRule ? List.of(rule) : List.of());
            when(limits.storefrontFields(store, rule.getFields())).thenReturn(List.of());
            var cache = new StorefrontCache(new SimpleMeterRegistry(), Duration.ofMinutes(1), 100);
            var controller = new PublicPersonalizationController(stores, rules, limits, mock(IntegrationLogService.class), cache);
            for (long product = 1; product <= 100; product++) {
                assertThat(controller.getFields(1L, product, "/", new MockHttpServletRequest()).enabled()).isFalse();
            }
            verify(stores, times(1)).findActiveByStoreId(1L);
            verify(rules, times(1)).findByStoreIdAndEnabledTrue(1L);
            verify(rules, never()).findWithFieldsByStoreIdAndProductId(anyLong(), anyLong());
        }
    }

    @Test void databaseFailureIsNotCachedAsEmptyStore() {
        var stores = mock(StoreRepository.class);
        var rules = mock(PersonalizationRuleRepository.class);
        var store = new Store(); store.setStoreId(1L);
        when(stores.findActiveByStoreId(1L)).thenReturn(Optional.of(store));
        when(rules.findByStoreIdAndEnabledTrue(1L)).thenThrow(new IllegalStateException("unavailable")).thenReturn(List.of());
        var controller = new PublicPersonalizationController(stores, rules, mock(PlanLimitService.class),
                mock(IntegrationLogService.class), new StorefrontCache(new SimpleMeterRegistry(), Duration.ofMinutes(1), 100));
        assertThatThrownBy(() -> controller.getFields(1L, 1L, "/", new MockHttpServletRequest())).isInstanceOf(IllegalStateException.class);
        assertThat(controller.getFields(1L, 2L, "/", new MockHttpServletRequest()).enabled()).isFalse();
        verify(rules, times(2)).findByStoreIdAndEnabledTrue(1L);
    }
}
