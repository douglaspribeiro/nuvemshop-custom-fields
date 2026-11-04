package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.*;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="analytics.ga4.measurement-id=G-MERCHANT123")
@AutoConfigureMockMvc
@Transactional
class MerchantAnalyticsPageTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    private MockHttpSession merchant() {
        var store=new Store(); store.setStoreId(990088L); store.setStoreName("Loja de teste"); store.setAccessToken("private-token");
        store.setStoreEmail("private-owner@example.com"); stores.saveAndFlush(store);
        var session=new MockHttpSession(); session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,store.getStoreId());
        return session;
    }
    @Test void rendersTheAuthenticatedStoreContextAndSanitizesThePageLocation() throws Exception {
        String html=mvc.perform(get("/admin/help").session(merchant()).param("storeId","1234").param("token","query-secret"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("G-MERCHANT123", "admin_page_view", "\"store_id\":\"990088\"", "\"admin_screen\":\"help\"",
            "window.location.origin + window.location.pathname")
            .doesNotContain("query-secret", "private-token", "private-owner@example.com");
    }
    @Test void operatorVisitsDoNotLoadTheMerchantAnalyticsTag() throws Exception {
        var session=merchant(); session.setAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY,true);
        String html=mvc.perform(get("/admin/help").session(session)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("googletagmanager.com/gtag/js", "admin_page_view");
    }
    @Test void anonymousBootstrapDoesNotIncludeAnyStoreIdentity() throws Exception {
        String html=mvc.perform(get("/admin/embedded")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("const merchantContext = {};").doesNotContain("\"store_id\":\"");
    }
}
