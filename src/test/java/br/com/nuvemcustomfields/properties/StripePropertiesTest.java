package br.com.nuvemcustomfields.properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class StripePropertiesTest {
    @ParameterizedTest
    @ValueSource(strings={"sk_test_fixture_only", "rk_test_fixture_only"})
    void sandboxAcceptsSecretAndRestrictedTestKeysAndRejectsThemInProduction(String key) {
        assertThat(properties(true,key).apiConfigured()).isTrue();
        assertThat(properties(true,key).configured()).isTrue();
        assertThat(properties(false,key).apiConfigured()).isFalse();
        assertThat(properties(false,key).operational()).isFalse();
    }
    @ParameterizedTest
    @ValueSource(strings={"sk_live_fixture_only", "rk_live_fixture_only"})
    void productionAcceptsSecretAndRestrictedLiveKeysAndRejectsThemInSandbox(String key) {
        assertThat(properties(false,key).apiConfigured()).isTrue();
        assertThat(properties(false,key).configured()).isTrue();
        assertThat(properties(true,key).apiConfigured()).isFalse();
        assertThat(properties(true,key).operational()).isFalse();
    }
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings={" ", "pk_test_fixture_only", "pk_live_fixture_only", "sk_org_fixture_only", "rk_test_", "rk_live_", "invalid"})
    void publicMalformedAndUnsupportedKeysRemainRejected(String key) {
        assertThat(properties(true,key).apiConfigured()).isFalse();
        assertThat(properties(false,key).apiConfigured()).isFalse();
    }
    @Test void acceptingRestrictedKeysDoesNotBypassWebhookAndNewSalesGates() {
        var noWebhook=new StripeProperties(true,true,"rk_test_fixture_only","","42",0,20);
        assertThat(noWebhook.apiConfigured()).isTrue(); assertThat(noWebhook.operational()).isFalse();
        var disabled=new StripeProperties(false,false,"rk_live_fixture_only","whsec_fixture","",0,20);
        assertThat(disabled.operational()).isTrue(); assertThat(disabled.configured()).isFalse();
    }
    private StripeProperties properties(boolean sandbox,String key) {
        return new StripeProperties(true,sandbox,key,"whsec_fixture","42",0,20);
    }
}
