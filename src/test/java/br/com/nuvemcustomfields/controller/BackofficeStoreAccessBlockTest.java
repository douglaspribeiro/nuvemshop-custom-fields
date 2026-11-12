package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class BackofficeStoreAccessBlockTest {
    private static final AtomicLong IDS = new AtomicLong(991182000);
    @Autowired StoreRepository stores;
    @Autowired StoreAccessBlockService blocks;
    @Autowired StoreDataErasureService erasure;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    long id;
    @BeforeEach void setup() {
        id = IDS.incrementAndGet();
        var store = new Store(); store.setStoreId(id); store.setAccessToken("test-only");
        store.setPlan(PlanType.PREMIUM_ULTRA); store.setManualPlanOverride(true); stores.saveAndFlush(store);
    }
    @AfterEach void cleanup() {
        erasure.erase(id);
        jdbc.update("delete from store_access_blocks where store_id=?", id);
    }
    MockHttpSession admin() { var session=new MockHttpSession(); session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true); return session; }
    @Test void requiresBackofficeSessionTokenAndExactIdentityConfirmation() throws Exception {
        mvc.perform(post("/backoffice/stores/"+id+"/force-uninstall").param("actionToken","x").param("confirmation",""+id))
                .andExpect(redirectedUrl("/backoffice/login"));
        var session=admin();String token=BackofficeStoreDepartureController.token(session);
        mvc.perform(post("/backoffice/stores/"+id+"/force-uninstall").session(session).param("actionToken","wrong").param("confirmation",""+id)).andExpect(status().isForbidden());
        mvc.perform(post("/backoffice/stores/"+id+"/force-uninstall").session(session).param("actionToken",token).param("confirmation","other")).andExpect(status().isBadRequest());
        assertThat(stores.findActiveByStoreId(id)).isPresent();
        mvc.perform(get("/backoffice/stores/"+id).session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Forçar uninstall e bloquear")));
        mvc.perform(post("/backoffice/stores/"+id+"/force-uninstall").session(session).param("actionToken",token).param("confirmation",""+id)).andExpect(redirectedUrl("/backoffice/stores/"+id));
        var merchant=new MockHttpSession();merchant.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,id);
        mvc.perform(get("/admin").session(merchant)).andExpect(status().is3xxRedirection());
        assertThat(stores.findActiveByStoreId(id)).isEmpty();
    }
    @Test void blockingIsIdempotentRevokesAccessAndSurvivesErasure() {
        blocks.forceUninstall(id);blocks.forceUninstall(id);
        var store=stores.findByStoreId(id).orElseThrow();
        assertThat(store.isActive()).isFalse(); assertThat(store.getAccessToken()).isNull();
        assertThat(store.getUninstalledAt()).isNotNull(); assertThat(store.getAccessBlockedAt()).isNotNull();
        assertThat(store.isManualPlanOverride()).isFalse(); assertThat(store.getPlan()).isEqualTo(PlanType.FREE);
        assertThat(jdbc.queryForObject("select count(*) from store_access_blocks where store_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from winback_outbox where store_id=?",Integer.class,id)).isZero();
        erasure.erase(id);
        assertThat(stores.findByStoreId(id)).isEmpty();
        assertThat(stores.isInstallationBlocked(id)).isTrue();
    }
}
