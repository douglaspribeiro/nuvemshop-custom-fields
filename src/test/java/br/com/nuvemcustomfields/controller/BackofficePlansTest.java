package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.service.PlanCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BackofficePlansTest {
    @Autowired MockMvc mvc;
    @Autowired PlanCatalogService catalog;

    @Test void requiresAuthenticationAndActionToken() throws Exception {
        mvc.perform(get("/backoffice/plans")).andExpect(status().is3xxRedirection());
        var session = admin();
        mvc.perform(post("/backoffice/plans").session(session).param("actionToken", "invalid")
                .param("planType", "PREMIUM").param("displayName", "Essencial").param("billingExternalId", "PREMIUM")
                .param("currency", "BRL").param("amount", "19.99").param("productLimit", "25")
                .param("fieldLimit", "3").param("imageProductLimit", "1").param("imageOptionLimit", "3").param("effectiveFrom", catalog.today().toString())).andExpect(status().isForbidden());
    }

    @Test void rendersPlansCreatesVersionAndRendersReport() throws Exception {
        var session = admin();
        var page = mvc.perform(get("/backoffice/plans").session(session)).andExpect(status().isOk()).andReturn();
        assertThat(page.getResponse().getContentAsString()).contains("Ultra", "Grátis", "50 produtos", "Pagamentos", "imageProductLimit", "imageOptionLimit", "25 opções de imagem por campo");
        String token = (String) session.getAttribute("planActionToken");
        mvc.perform(post("/backoffice/plans").session(session).param("actionToken", token)
                .param("planType", "PREMIUM").param("displayName", "Essencial").param("billingExternalId", "PREMIUM")
                .param("currency", "BRL").param("amount", "19.99").param("productLimit", "25")
                .param("fieldLimit", "3").param("imageProductLimit", "1").param("imageOptionLimit", "3").param("effectiveFrom", catalog.today().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));
        assertThat(catalog.activePlan(PlanType.PREMIUM).getProductLimit()).isEqualTo(25);
        assertThat(catalog.activePlan(PlanType.PREMIUM).getImageProductLimit()).isEqualTo(1);
        assertThat(catalog.activePlan(PlanType.PREMIUM).getImageOptionLimit()).isEqualTo(3);
        mvc.perform(get("/backoffice/plans").session(session)).andExpect(status().isOk());
        mvc.perform(get("/backoffice/reports").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Próximas cobranças")));
    }
    @Test void publicPricingDisplaysImageEntitlementsFromTheCatalog() throws Exception {
        mvc.perform(get("/precos/")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sem opções com imagem")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Até 3 opções de imagem por campo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Até 8 opções de imagem por campo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Até 25 opções de imagem por campo")));
    }
    private MockHttpSession admin() {
        var session = new MockHttpSession(); session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true); return session;
    }
}
