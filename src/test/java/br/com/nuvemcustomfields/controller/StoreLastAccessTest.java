package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest @AutoConfigureMockMvc
class StoreLastAccessTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    private static final AtomicLong IDS=new AtomicLong(996655000);
    private Store store;
    private MockHttpSession merchant;
    private MockHttpSession owner;

    @BeforeEach void setup() {
        store=new Store();store.setStoreId(IDS.incrementAndGet());store.setAccessToken("test");store.setStoreName("Último acesso teste");
        store.setStoreCountryCode("BR");stores.saveAndFlush(store);
        merchant=new MockHttpSession();merchant.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,store.getStoreId());
        owner=new MockHttpSession();owner.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);
    }
    Instant access() { return stores.findByStoreId(store.getStoreId()).orElseThrow().getLastAdminAccessAt(); }

    @Test void recordsMerchantPagesRendersDatesAndThrottlesRepeatedVisits() throws Exception {
        Instant before=Instant.now();
        mvc.perform(get("/admin/help").session(merchant)).andExpect(status().isOk());
        Instant timestamp=access();assertThat(timestamp).isAfterOrEqualTo(before).isBeforeOrEqualTo(Instant.now());
        mvc.perform(get("/admin/settings/style").session(merchant)).andExpect(status().isOk());
        assertThat(access()).isEqualTo(timestamp);
        for(String path:new String[]{"/backoffice","/backoffice/stores","/backoffice/stores/"+store.getStoreId()}) {
            mvc.perform(get(path).session(owner)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("Último acesso em")))
                    .andExpect(content().string(containsString("data-local-time=\""+timestamp+"\"")));
        }
    }
    @Test void ignoresStorefrontBackgroundPollingOwnerVisitsAndUnauthenticatedRequests() throws Exception {
        mvc.perform(get("/public/stores/"+store.getStoreId()+"/personalization").param("productId","123")).andExpect(status().isOk());
        mvc.perform(get("/admin/billing/status").session(merchant)).andExpect(status().isOk());
        mvc.perform(get("/admin/help")).andExpect(status().is3xxRedirection());
        merchant.setAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY,true);
        mvc.perform(get("/admin/help").session(merchant)).andExpect(status().isOk());
        assertThat(access()).isNull();
        mvc.perform(get("/backoffice/stores").session(owner)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Último acesso: não registrado")));
    }
    @Test void staleStoreSavesAndDelayedRequestsCannotOverwriteRecentAccess() {
        Store stale=stores.findByStoreId(store.getStoreId()).orElseThrow();
        Instant recorded=Instant.parse("2026-10-05T10:00:00Z");
        assertThat(stores.recordAdminAccess(store.getStoreId(),recorded,recorded.minusSeconds(60))).isEqualTo(1);
        stale.setStoreName("Nome atualizado");stores.saveAndFlush(stale);
        assertThat(access()).isEqualTo(recorded);
        assertThat(stores.recordAdminAccess(store.getStoreId(),recorded.minusSeconds(120),recorded.minusSeconds(180))).isZero();
        assertThat(access()).isEqualTo(recorded);
        assertThat(stores.recordAdminAccess(store.getStoreId(),recorded.plusSeconds(61),recorded.plusSeconds(1))).isEqualTo(1);
        assertThat(access()).isEqualTo(recorded.plusSeconds(61));
    }
}
