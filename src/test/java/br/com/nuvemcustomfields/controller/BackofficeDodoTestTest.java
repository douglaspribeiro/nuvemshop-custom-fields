package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.DodoTestGateway;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BackofficeDodoTestTest {
    @Autowired MockMvc mvc;
    @Autowired PaymentCatalogPriceRepository catalog;
    @MockitoSpyBean DodoTestGateway gateway;
    @MockitoSpyBean br.com.nuvemcustomfields.payment.DodoLiveGateway live;
    MockHttpSession session;
    @BeforeEach void setup() {
        session = new MockHttpSession(); session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
        doNothing().when(gateway).validateCatalog(any());
    }
    @Test void pageAndBindingAreProtectedAndNewProductRemainsDisabled() throws Exception {
        mvc.perform(get("/backoffice/payments/dodo-test")).andExpect(redirectedUrl("/backoffice/login"));
        mvc.perform(get("/backoffice/payments/dodo-test").session(session))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        String token = (String) session.getAttribute("dodoTestActionToken");
        mvc.perform(post("/backoffice/payments/dodo-test/bind").session(session)
                .param("actionToken",token).param("plan","PREMIUM").param("countryCode","MX")
                .param("productId","pdt_test").param("currency","USD").param("amount","1.00"))
                .andExpect(redirectedUrl("/backoffice/payments/dodo-test")).andExpect(flash().attributeExists("message"));
        var price = catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.DODO_TEST,PaymentEnvironment.SANDBOX,"MX",PlanType.PREMIUM).orElseThrow();
        assertThat(price.isEnabled()).isFalse(); assertThat(price.isValidated()).isTrue(); assertThat(price.getProviderPriceId()).isEqualTo("pdt_test");
        verify(gateway).validateCatalog(any());
        mvc.perform(post("/backoffice/payments/dodo-test/bind").session(session)
                .param("actionToken","bad").param("plan","PREMIUM").param("countryCode","MX")
                .param("productId","pdt_test").param("currency","USD").param("amount","1.00"))
                .andExpect(status().isForbidden());
    }
    @Test void liveBindingUsesASeparateSessionTokenAndProductionCatalog() throws Exception {
        doNothing().when(live).validateCatalog(any());
        mvc.perform(get("/backoffice/payments/dodo-test").session(session)).andExpect(status().isOk());
        String testToken=(String)session.getAttribute("dodoTestActionToken");
        mvc.perform(get("/backoffice/payments/dodo").session(session)).andExpect(status().isOk());
        String liveToken=(String)session.getAttribute("dodoLiveActionToken");
        mvc.perform(post("/backoffice/payments/dodo/bind").session(session).param("actionToken",testToken)
                .param("plan","PREMIUM").param("countryCode","CL").param("productId","pdt_live").param("currency","CLP").param("amount","4199"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/backoffice/payments/dodo/bind").session(session).param("actionToken",liveToken)
                .param("plan","PREMIUM").param("countryCode","CL").param("productId","pdt_live").param("currency","CLP").param("amount","4199"))
                .andExpect(flash().attributeExists("message"));
        var price=catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.DODO,PaymentEnvironment.PRODUCTION,"CL",PlanType.PREMIUM).orElseThrow();
        assertThat(price.isEnabled()).isFalse(); assertThat(price.getCurrency()).isEqualTo("CLP"); verify(live).validateCatalog(any());
    }

    @Test void rejectsBrazilWithoutCallingTheGateway() throws Exception {
        mvc.perform(get("/backoffice/payments/dodo-test").session(session)).andExpect(status().isOk());
        mvc.perform(post("/backoffice/payments/dodo-test/bind").session(session)
                .param("actionToken",(String)session.getAttribute("dodoTestActionToken")).param("plan","PREMIUM").param("countryCode","BR")
                .param("productId","pdt_test").param("currency","USD").param("amount","1.00"))
                .andExpect(flash().attributeExists("error"));
        verify(gateway,never()).validateCatalog(any());
    }
}
