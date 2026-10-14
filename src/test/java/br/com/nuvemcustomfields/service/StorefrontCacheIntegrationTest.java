package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.controller.PublicPersonalizationController;
import br.com.nuvemcustomfields.config.StorefrontTrafficMetricsFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StorefrontCacheIntegrationTest {
    @Autowired StoreRepository stores;
    @Autowired br.com.nuvemcustomfields.repository.PersonalizationRuleRepository rules;
    @Autowired br.com.nuvemcustomfields.repository.PersonalizationFieldRepository fields;
    @Autowired StorefrontCache cache;
    @Autowired PublicPersonalizationController controller;
    @Autowired PlatformTransactionManager transactions;

    @Test void committedChangesInvalidateCachedStyleAndStoreActivityWhileRollbackPreservesThem() {
        Store store = new Store(); store.setStoreId(998877L); store.setStoreName("Cache test");
        store.setCheckoutTextColor("#111111"); stores.saveAndFlush(store);
        try {
            assertThat(controller.getStyle(998877L, new MockHttpServletRequest()).checkoutTextColor()).isEqualTo("#111111");
            var hit = new MockHttpServletRequest();
            controller.getStyle(998877L, hit);
            assertThat(hit.getAttribute(StorefrontTrafficMetricsFilter.ACTIVE_STORE_ID_ATTRIBUTE)).isEqualTo(998877L);
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var changed = stores.findActiveByStoreId(998877L).orElseThrow();
                changed.setCheckoutTextColor("#222222"); stores.saveAndFlush(changed);
                status.setRollbackOnly();
            });
            assertThat(controller.getStyle(998877L, new MockHttpServletRequest()).checkoutTextColor()).isEqualTo("#111111");
            store = stores.findActiveByStoreId(998877L).orElseThrow();
            store.setCheckoutTextColor("#333333"); stores.saveAndFlush(store);
            assertThat(controller.getStyle(998877L, new MockHttpServletRequest()).checkoutTextColor()).isEqualTo("#333333");
            assertThat(cache.get("active", 998877L, null, () -> stores.findActiveByStoreId(998877L).isPresent())).isTrue();
            store.setUninstalledAt(java.time.Instant.now()); stores.saveAndFlush(store);
            assertThat(controller.getStyle(998877L, new MockHttpServletRequest()).checkoutTextColor()).isNull();
            assertThat(cache.get("active", 998877L, null, () -> stores.findActiveByStoreId(998877L).isPresent())).isFalse();
        } finally { stores.findByStoreId(998877L).ifPresent(stores::delete); }
    }

    @Test void cachesPersonalizationPreservingProductMetricsAndInvalidatesFieldChanges() {
        Store store = new Store(); store.setStoreId(998878L); store.setStoreName("Product cache");
        stores.saveAndFlush(store);
        var rule = new br.com.nuvemcustomfields.entity.PersonalizationRule();
        rule.setStoreId(998878L); rule.setProductId(10L); rule.setProductName("Product"); rule.setEnabled(true);
        rule = rules.saveAndFlush(rule);
        var field = new br.com.nuvemcustomfields.entity.PersonalizationField();
        field.setRule(rule); field.setLabel("Original"); field = fields.saveAndFlush(field);
        try {
            var first = controller.getFields(998878L, 10L, "/first", new MockHttpServletRequest());
            assertThat(first.enabled()).isTrue();
            assertThat(first.fields()).hasSize(1);
            var request = new MockHttpServletRequest();
            assertThat(controller.getFields(998878L, 10L, "/second", request)).isSameAs(first);
            assertThat(request.getAttribute(StorefrontTrafficMetricsFilter.PERSONALIZED_PRODUCT_ID_ATTRIBUTE)).isEqualTo(10L);
            field.setLabel("Updated"); fields.saveAndFlush(field);
            var updated = controller.getFields(998878L, 10L, "/third", new MockHttpServletRequest());
            assertThat(updated).isNotSameAs(first);
            assertThat(updated.fields().getFirst().label()).isEqualTo("Updated");
        } finally {
            fields.deleteById(field.getId()); rules.deleteById(rule.getId());
            stores.findByStoreId(998878L).ifPresent(stores::delete);
        }
    }
}
