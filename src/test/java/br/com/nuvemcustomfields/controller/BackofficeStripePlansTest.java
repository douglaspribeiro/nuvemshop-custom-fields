package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:stripe_plans;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "payments.webhook-processing-delay-ms=3600000"})
@AutoConfigureMockMvc
class BackofficeStripePlansTest {
    @Autowired MockMvc mvc;
    @Autowired StripeCatalogPublicationRepository publications;
    @Autowired PaymentCatalogPriceRepository catalog;
    @Autowired PaymentRoutingRuleRepository routes;
    @TestBean(methodName="stripeGateway") StripeGateway gateway;
    static StripeGateway stripeGateway() {
        var gateway = mock(StripeGateway.class); when(gateway.provider()).thenReturn(PaymentProviderType.STRIPE);
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX); return gateway;
    }
    @BeforeEach void setup() {
        reset(gateway); when(gateway.provider()).thenReturn(PaymentProviderType.STRIPE);
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX); when(gateway.apiConfigured()).thenReturn(true);
        when(gateway.validatedProductId(any())).thenReturn("prod_saved");
        publications.deleteAll(); catalog.findAll().stream().filter(p -> p.getProvider()==PaymentProviderType.STRIPE).forEach(catalog::delete);
    }
    @Test void createsFromPlansPageAndPersistsPriceAcrossReloadWithoutEnablingRoute() throws Exception {
        when(gateway.createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),anyString()))
                .thenReturn(new ObjectMapper().readTree("{\"id\":\"prod_saved\",\"default_price\":\"price_saved\"}"));
        var session=admin(); mvc.perform(get("/backoffice/plans").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Criar produto e preço na Stripe")));
        String token=(String)session.getAttribute("planActionToken");
        mvc.perform(create(session,token)).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));
        var price=catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.STRIPE,PaymentEnvironment.SANDBOX,"AR",PlanType.PREMIUM).orElseThrow();
        assertThat(price.getProviderPriceId()).isEqualTo("price_saved"); assertThat(price.isEnabled()).isFalse(); assertThat(price.isValidated()).isTrue();
        assertThat(publications.findAll()).hasSize(1); assertThat(publications.findAll().getFirst().getProductId()).isEqualTo("prod_saved");
        mvc.perform(get("/backoffice/plans").session(session)).andExpect(content().string(org.hamcrest.Matchers.containsString("price_saved")));
        mvc.perform(create(session,token)).andExpect(status().is3xxRedirection());
        verify(gateway,times(1)).createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),anyString());
        assertThat(routes.findByCountryCodeIgnoreCase("AR").map(r -> r.getProvider()==PaymentProviderType.STRIPE && r.isEnabled()).orElse(false)).isFalse();
    }
    @Test void resumesLostResponseWithItsOriginalKey() throws Exception {
        when(gateway.createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),anyString()))
                .thenThrow(new PaymentGatewayException("Resposta indeterminada"))
                .thenReturn(new ObjectMapper().readTree("{\"id\":\"prod_saved\",\"default_price\":\"price_saved\"}"));
        var session=admin(); mvc.perform(get("/backoffice/plans").session(session)); String token=(String)session.getAttribute("planActionToken");
        mvc.perform(create(session,token)).andExpect(flash().attributeExists("error"));
        var operation=publications.findAll().getFirst(); assertThat(operation.getStatus()).isEqualTo("UNKNOWN");
        mvc.perform(post("/backoffice/plans/stripe/"+operation.getId()+"/resume").session(session).param("actionToken",token)).andExpect(flash().attributeExists("message"));
        assertThat(publications.findById(operation.getId()).orElseThrow().getPriceId()).isEqualTo("price_saved");
        verify(gateway,times(2)).createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),eq(operation.getPublicationKey()));
    }
    @Test void requiresAuthenticationAndActionTokenForCreationResumeAndBind() throws Exception {
        mvc.perform(create(new MockHttpSession(),"bad")).andExpect(status().is3xxRedirection());
        mvc.perform(create(admin(),"bad")).andExpect(status().isForbidden());
        mvc.perform(post("/backoffice/plans/stripe/1/resume").session(admin()).param("actionToken","bad")).andExpect(status().isForbidden());
        mvc.perform(post("/backoffice/plans/stripe/bind").session(admin()).param("actionToken","bad").param("plan","PREMIUM")
                .param("environment","SANDBOX").param("currency","ARS").param("amount","5599").param("priceId","price_saved")).andExpect(status().isForbidden());
        verify(gateway,never()).createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),anyString());
        verify(gateway,never()).validatedProductId(any());
    }
    @Test void bindsAnExistingMonthlyPriceWithoutCreatingAProduct() throws Exception {
        var session=admin(); mvc.perform(get("/backoffice/plans").session(session));
        mvc.perform(post("/backoffice/plans/stripe/bind").session(session).param("actionToken",(String)session.getAttribute("planActionToken"))
                .param("plan","PREMIUM_PLUS").param("environment","SANDBOX").param("currency","ARS").param("amount","8399").param("priceId","price_existing"))
                .andExpect(flash().attributeExists("message"));
        var price=catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.STRIPE,PaymentEnvironment.SANDBOX,"AR",PlanType.PREMIUM_PLUS).orElseThrow();
        assertThat(price.getProviderPriceId()).isEqualTo("price_existing"); assertThat(price.isEnabled()).isFalse();
        verify(gateway,never()).createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),anyString());
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create(MockHttpSession session,String token) {
        return post("/backoffice/plans/stripe/create").session(session).param("actionToken",token).param("plan","PREMIUM")
                .param("environment","SANDBOX").param("name","Essencial").param("description","Mensal").param("currency","ARS").param("amount","5599");
    }
    MockHttpSession admin() { var session=new MockHttpSession(); session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true); return session; }
}
