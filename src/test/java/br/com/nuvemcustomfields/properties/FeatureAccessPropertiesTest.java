package br.com.nuvemcustomfields.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureAccessPropertiesTest {
    @Test void grantsOnlyTheConfiguredFeatureToTheConfiguredStore() {
        var access = new FeatureAccessProperties("42:apps-menu, 99:product-complements");

        assertThat(access.allows(42L, FeatureAccessProperties.Feature.APPS_MENU)).isTrue();
        assertThat(access.allows(42L, FeatureAccessProperties.Feature.PRODUCT_COMPLEMENTS)).isFalse();
        assertThat(access.allows(99L, FeatureAccessProperties.Feature.APPS_MENU)).isFalse();
        assertThat(access.allows(142L, FeatureAccessProperties.Feature.APPS_MENU)).isFalse();
    }

    @Test void malformedConfigurationOrFeatureNeverGrantsAccess() {
        for (String value : new String[]{null, "", "42:apps-menu,invalid", "42:Apps-menu", "42:apps-menu,"}) {
            assertThat(new FeatureAccessProperties(value).allows(42L, FeatureAccessProperties.Feature.APPS_MENU)).isFalse();
        }
        assertThat(new FeatureAccessProperties("42:apps-menu").allows(42L, FeatureAccessProperties.Feature.PRODUCT_COMPLEMENTS)).isFalse();
    }
}
