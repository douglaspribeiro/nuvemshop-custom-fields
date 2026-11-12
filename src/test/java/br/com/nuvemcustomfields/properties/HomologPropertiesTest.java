package br.com.nuvemcustomfields.properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class HomologPropertiesTest {
    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings={"0", " 0 ", " ", "bad", "42,0", "*,42", "42,", "42,,99", "-42", "042"})
    void disabledOrMalformedConfigurationNeverGrantsAccess(String ids) {
        var policy = new HomologProperties(ids);
        assertThat(policy.allows(42L)).isFalse();
        assertThat(policy.allowsAllStores()).isFalse();
    }
    @Test void wildcardAuthorizesIdentifiedStoresAndListUsesExactMembership() {
        var all = new HomologProperties(" * ");
        assertThat(all.allowsAllStores()).isTrue();
        assertThat(all.allows(42L)).isTrue();
        assertThat(all.allows(null)).isFalse();
        assertThat(all.allows(0L)).isFalse();
        var selected = new HomologProperties("42, 99");
        assertThat(selected.allows(42L)).isTrue();
        assertThat(selected.allows(99L)).isTrue();
        assertThat(selected.allows(142L)).isFalse();
    }
}
