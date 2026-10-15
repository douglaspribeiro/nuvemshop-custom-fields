package br.com.nuvemcustomfields.properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class DodoTestPropertiesTest {
    DodoTestProperties properties(String ids) { return new DodoTestProperties(true,"dodo_test_test","secret",ids,20); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","0"," 0 ","42, 0"})
    void emptyOrZeroAllowsAllIdentifiedStores(String ids) {
        assertThat(properties(ids).allowsAllStores()).isTrue();
        assertThat(properties(ids).allows(42L)).isTrue();
        assertThat(properties(ids).allows(99L)).isTrue();
        assertThat(properties(ids).allows(null)).isFalse();
    }
    @Test void explicitListOnlyAllowsItsMembers() {
        var settings=properties("42, 99");
        assertThat(settings.allowsAllStores()).isFalse();
        assertThat(settings.allows(42L)).isTrue();assertThat(settings.allows(99L)).isTrue();
        assertThat(settings.allows(123L)).isFalse();
        assertThat(properties("bad, ,000").allows(42L)).isFalse();
    }
}
