package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.NuvemshopBillingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PremiumBonusPageTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @MockitoBean NuvemshopBillingService billing;

    @Test
    void grantsPersistsAndRendersBonusWithoutBillingAndRejectsDuplicate() throws Exception {
        Store store = new Store();
        store.setStoreId(7654321L);
        store.setAccessToken("test");
        stores.save(store);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
        String path = "/backoffice/stores/7654321";

        mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Conceder Premium por 30 dias")));
        mvc.perform(post(path + "/premium-bonus").session(session))
                .andExpect(redirectedUrl(path));
        Store saved = stores.findByStoreId(7654321L).orElseThrow();
        assertThat(saved.getEffectivePlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(saved.getPremiumBonusExpiresAt()).isNotNull();
        mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Brasilia")))
                .andExpect(content().string(not(containsString("Conceder Premium por 30 dias"))));
        mvc.perform(post(path + "/premium-bonus").session(session))
                .andExpect(flash().attributeExists("error"));
        verifyNoInteractions(billing);
    }

    @Test
    void blocksDirectUpgradeEvenWithAnAuthenticatedStore() throws Exception {
        Store store = new Store();
        store.setStoreId(7654322L);
        store.setAccessToken("test");
        stores.save(store);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, 7654322L);
        mvc.perform(get("/admin/billing").session(session)).andExpect(redirectedUrl("/admin"));
        mvc.perform(post("/admin/billing/subscribe").param("plan", "PREMIUM").session(session))
                .andExpect(redirectedUrl("/admin"));
        verifyNoInteractions(billing);
    }

    @Test
    void bonusRequiresBackofficeAuthentication() throws Exception {
        mvc.perform(post("/backoffice/stores/7654321/premium-bonus"))
                .andExpect(redirectedUrl("/backoffice/login"));
    }
}
