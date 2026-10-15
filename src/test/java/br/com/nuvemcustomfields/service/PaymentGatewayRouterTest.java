package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.PaymentGateway;
import br.com.nuvemcustomfields.repository.PaymentRoutingRuleRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentGatewayRouterTest {
    @Test void usesOnlyTheEnabledExplicitCountryRoute() {
        PaymentGateway efi=gateway(PaymentProviderType.EFI,PaymentEnvironment.PRODUCTION);
        PaymentGateway paddle=gateway(PaymentProviderType.PADDLE,PaymentEnvironment.SANDBOX);
        PaymentRoutingRuleRepository rules=mock(PaymentRoutingRuleRepository.class);
        PaymentRoutingRule mx=rule("MX",PaymentProviderType.PADDLE,PaymentEnvironment.SANDBOX,true);
        when(rules.findByCountryCodeIgnoreCase("MX")).thenReturn(Optional.of(mx));
        Store store=new Store(); store.setStoreCountryCode("MX");
        PaymentGatewayRouter router=new PaymentGatewayRouter(List.of(efi,paddle),rules);
        assertThat(router.requireForStore(store)).isSameAs(paddle);
        verify(efi,never()).supports(any());
    }

    @Test void blocksCountriesWithoutRulesAndDisabledRules() {
        PaymentGateway paddle=gateway(PaymentProviderType.PADDLE,PaymentEnvironment.SANDBOX);
        PaymentRoutingRuleRepository rules=mock(PaymentRoutingRuleRepository.class);
        when(rules.findByCountryCodeIgnoreCase("AR")).thenReturn(Optional.of(
                rule("AR",PaymentProviderType.PADDLE,PaymentEnvironment.SANDBOX,false)));
        Store ar=new Store(); ar.setStoreCountryCode("AR"); Store co=new Store(); co.setStoreCountryCode("CO");
        PaymentGatewayRouter router=new PaymentGatewayRouter(List.of(paddle),rules);
        assertThat(router.forStore(ar)).isEmpty();
        assertThat(router.forStore(co)).isEmpty();
    }

    @Test void existingSubscriptionOperationsDoNotDependOnCountryRoute() {
        PaymentGateway paddle=gateway(PaymentProviderType.PADDLE,PaymentEnvironment.SANDBOX);
        PaymentGatewayRouter router=new PaymentGatewayRouter(List.of(paddle),mock(PaymentRoutingRuleRepository.class));
        assertThat(router.require(PaymentProviderType.PADDLE)).isSameAs(paddle);
    }

    @Test void testGatewayOnlyOverridesTheExplicitlySupportedStore() {
        PaymentGateway dodo = gateway(PaymentProviderType.DODO_TEST, PaymentEnvironment.SANDBOX);
        PaymentGateway paddle = gateway(PaymentProviderType.PADDLE, PaymentEnvironment.PRODUCTION);
        var rules = mock(PaymentRoutingRuleRepository.class);
        when(rules.findByCountryCodeIgnoreCase("MX")).thenReturn(Optional.of(rule("MX", PaymentProviderType.PADDLE, PaymentEnvironment.PRODUCTION, true)));
        Store test = new Store(); test.setStoreId(42L); test.setStoreCountryCode("MX");
        Store other = new Store(); other.setStoreId(43L); other.setStoreCountryCode("MX");
        when(dodo.supports(test)).thenReturn(true); when(dodo.supports(other)).thenReturn(false);
        var router = new PaymentGatewayRouter(List.of(dodo, paddle), rules);
        assertThat(router.requireForStore(test)).isSameAs(dodo);
        assertThat(router.requireForStore(other)).isSameAs(paddle);
        when(dodo.configured()).thenReturn(false);
        assertThat(router.requireForStore(test)).isSameAs(paddle);
    }

    private static PaymentGateway gateway(PaymentProviderType provider,PaymentEnvironment environment) {
        PaymentGateway gateway=mock(PaymentGateway.class); when(gateway.provider()).thenReturn(provider);
        when(gateway.environment()).thenReturn(environment); when(gateway.configured()).thenReturn(true);
        when(gateway.supports(any())).thenReturn(true); return gateway;
    }
    private static PaymentRoutingRule rule(String country,PaymentProviderType provider,PaymentEnvironment environment,boolean enabled) {
        PaymentRoutingRule rule=new PaymentRoutingRule(); rule.setCountryCode(country); rule.setProvider(provider);
        rule.setEnvironment(environment); rule.setEnabled(enabled); rule.setUpdatedBy("test"); return rule;
    }
}
