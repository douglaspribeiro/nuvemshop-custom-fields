package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.PaymentConfigurationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:stripe_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "payments.webhook-processing-delay-ms=3600000"})
@AutoConfigureMockMvc
class BackofficeStripeConfigurationTest {
    @Autowired MockMvc mvc;
    @Autowired PaymentCatalogPriceRepository catalog;
    @Autowired PaymentRoutingRuleRepository routes;
    @Autowired PaymentConfigurationService configuration;
    @TestBean(methodName="stripeGateway") StripeGateway gateway;
    static StripeGateway stripeGateway() {
        var gateway = mock(StripeGateway.class);
        when(gateway.provider()).thenReturn(PaymentProviderType.STRIPE);
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX); return gateway;
    }
    @BeforeEach void setup() {
        reset(gateway); when(gateway.provider()).thenReturn(PaymentProviderType.STRIPE);
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX); when(gateway.configured()).thenReturn(true);
        catalog.findAll().stream().filter(p -> p.getProvider()==PaymentProviderType.STRIPE).forEach(catalog::delete);
        routes.deleteAll();
    }
    @Test void pageOffersStripePriceIdsAndCanReplaceAnInvisibleHistoricalPaddleRoute() throws Exception {
        var route = new PaymentRoutingRule(); route.setCountryCode("AR"); route.setProvider(PaymentProviderType.PADDLE);
        route.setEnvironment(PaymentEnvironment.SANDBOX); route.setUpdatedBy("test"); routes.saveAndFlush(route);
        var price = price(PlanType.PREMIUM);
        mvc.perform(get("/backoffice/payments").session(admin())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Salvar rota Stripe AR")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("price_...")));
        var disabled = configuration.savePrice(price.getId(),"ARS",new BigDecimal("5599.00"),"price_1",false);
        assertThat(disabled.enabled()).isFalse(); verify(gateway,never()).validateCatalog(any());
        assertThat(routes.findByCountryCodeIgnoreCase("AR").orElseThrow().getProvider()).isEqualTo(PaymentProviderType.PADDLE);
    }
    @Test void validatesRemoteCatalogBeforeEnablingTheArgentinaRoute() {
        for (PlanType plan : java.util.List.of(PlanType.PREMIUM,PlanType.PREMIUM_PLUS)) {
            var price = price(plan);
            assertThat(configuration.savePrice(price.getId(),"ARS",new BigDecimal("5599.00"),"price_"+plan.name(),true).validated()).isTrue();
        }
        configuration.saveRoute("AR",PaymentProviderType.STRIPE,PaymentEnvironment.SANDBOX,true,"test");
        assertThat(routes.findByCountryCodeIgnoreCase("AR").orElseThrow().getProvider()).isEqualTo(PaymentProviderType.STRIPE);
        verify(gateway,times(4)).validateCatalog(any());
        assertThatThrownBy(() -> configuration.saveRoute("BR",PaymentProviderType.STRIPE,PaymentEnvironment.SANDBOX,true,"test"))
                .hasMessageContaining("Argentina");
        assertThatThrownBy(() -> configuration.saveRoute("AR",PaymentProviderType.STRIPE,PaymentEnvironment.PRODUCTION,true,"test"))
                .hasMessageContaining("ambiente");
    }
    @Test void failedRemoteValidationPreservesIdAndLeavesPriceDisabled() {
        var price=price(PlanType.PREMIUM);
        doThrow(new IllegalArgumentException("Preço Stripe divergente")).when(gateway).validateCatalog(any());
        var result=configuration.savePrice(price.getId(),"ARS",new BigDecimal("5599.00"),"price_wrong",true);
        assertThat(result.validated()).isFalse();
        var saved=catalog.findById(price.getId()).orElseThrow(); assertThat(saved.isEnabled()).isFalse();
        assertThat(saved.getProviderPriceId()).isEqualTo("price_wrong");
        assertThatThrownBy(() -> configuration.saveRoute("AR",PaymentProviderType.STRIPE,PaymentEnvironment.SANDBOX,true,"test"))
                .hasMessageContaining("incompleto");
    }
    @Test void webhookRejectsUnsignedPayloadOnBothProxyPaths() throws Exception {
        when(gateway.verifyNotification(anyString(),isNull(),isNull(),isNull())).thenThrow(new PaymentGatewayException("invalid"));
        mvc.perform(post("/webhooks/stripe").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(post("/prod/webhooks/stripe").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
    }
    PaymentCatalogPrice price(PlanType plan) {
        var price = new PaymentCatalogPrice(); price.setProvider(PaymentProviderType.STRIPE); price.setEnvironment(PaymentEnvironment.SANDBOX);
        price.setCountryCode("AR"); price.setPlan(plan); price.setCurrency("ARS"); price.setAmountValue(new BigDecimal("5599.00"));
        price.setTaxMode("inclusive"); price.setEnabled(false); return catalog.saveAndFlush(price);
    }
    MockHttpSession admin() { var session = new MockHttpSession(); session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true); return session; }
}
