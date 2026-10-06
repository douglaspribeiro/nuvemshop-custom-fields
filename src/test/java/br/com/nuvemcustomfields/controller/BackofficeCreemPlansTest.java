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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:creem_controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "payments.webhook-processing-delay-ms=3600000"})
@AutoConfigureMockMvc
class BackofficeCreemPlansTest {
    @Autowired MockMvc mvc;
    @Autowired CreemCatalogPublicationRepository publications;
    @Autowired PaymentCatalogPriceRepository catalog;
    @Autowired PaymentRoutingRuleRepository routes;
    @TestBean(methodName="testGateway") CreemGateway gateway;
    static CreemGateway testGateway() {
        var gateway = mock(CreemGateway.class);
        when(gateway.provider()).thenReturn(PaymentProviderType.CREEM);
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        return gateway;
    }
    @BeforeEach void setup() {
        reset(gateway); when(gateway.provider()).thenReturn(PaymentProviderType.CREEM);
        publications.deleteAll(); catalog.findAll().stream().filter(p -> p.getProvider()==PaymentProviderType.CREEM).forEach(catalog::delete);
        when(gateway.apiConfigured()).thenReturn(true); when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
    }
    @Test void createsViaPlansApiAndPersistsCodeAcrossPageReloadWithoutEnablingRoute() throws Exception {
        when(gateway.createProduct(anyString(),anyString(),anyString(),any(),anyString(),anyString()))
                .thenReturn(new ObjectMapper().readTree("{\"id\":\"prod_saved\"}"));
        var session = admin(); mvc.perform(get("/backoffice/plans").session(session)).andExpect(status().isOk());
        mvc.perform(create(session,(String)session.getAttribute("planActionToken"))).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));
        var price = catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.CREEM,PaymentEnvironment.SANDBOX,"MX",PlanType.PREMIUM).orElseThrow();
        assertThat(price.getProviderPriceId()).isEqualTo("prod_saved"); assertThat(price.isEnabled()).isFalse();
        assertThat(publications.findAll()).hasSize(1); assertThat(publications.findAll().getFirst().getStatus()).isEqualTo("LINKED");
        mvc.perform(get("/backoffice/plans").session(session)).andExpect(content().string(org.hamcrest.Matchers.containsString("prod_saved")));
        mvc.perform(create(session,(String)session.getAttribute("planActionToken"))).andExpect(status().is3xxRedirection());
        verify(gateway,times(1)).createProduct(anyString(),anyString(),anyString(),any(),anyString(),anyString());
        assertThat(routes.findByCountryCodeIgnoreCase("MX").map(r -> r.getProvider()==PaymentProviderType.CREEM && r.isEnabled()).orElse(false)).isFalse();
    }
    @Test void failedRemoteResponseKeepsOperationAndResumeUsesItsOriginalKey() throws Exception {
        when(gateway.createProduct(anyString(),anyString(),anyString(),any(),anyString(),anyString()))
                .thenThrow(new PaymentGatewayException("Resposta indeterminada"))
                .thenReturn(new ObjectMapper().readTree("{\"id\":\"prod_recovered\"}"));
        var session = admin(); mvc.perform(get("/backoffice/plans").session(session)); String token = (String)session.getAttribute("planActionToken");
        mvc.perform(create(session,token)).andExpect(flash().attributeExists("error"));
        var operation = publications.findAll().getFirst(); assertThat(operation.getStatus()).isEqualTo("UNKNOWN");
        mvc.perform(post("/backoffice/plans/creem/"+operation.getId()+"/resume").session(session).param("actionToken",token)).andExpect(flash().attributeExists("message"));
        assertThat(publications.findById(operation.getId()).orElseThrow().getProductId()).isEqualTo("prod_recovered");
        verify(gateway,times(2)).createProduct(anyString(),anyString(),anyString(),any(),anyString(),eq(operation.getPublicationKey()));
    }
    @Test void requiresAuthenticationAndTokenBeforeAnyRemoteCreation() throws Exception {
        mvc.perform(create(new MockHttpSession(),"bad")).andExpect(status().is3xxRedirection());
        mvc.perform(create(admin(),"bad")).andExpect(status().isForbidden());
        verify(gateway,never()).createProduct(anyString(),anyString(),anyString(),any(),anyString(),anyString());
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create(MockHttpSession session,String token) {
        return post("/backoffice/plans/creem/create").session(session).param("actionToken",token).param("plan","PREMIUM")
                .param("environment","SANDBOX").param("countryCode","MX").param("name","Essencial").param("description","Mensal")
                .param("currency","USD").param("amount","9.99").param("taxMode","inclusive");
    }
    MockHttpSession admin() { var session = new MockHttpSession(); session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true); return session; }
}
