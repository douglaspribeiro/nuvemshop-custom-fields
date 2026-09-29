package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BackofficeUninstalledStoresPageTest {

    private static final long ACTIVE_ID = 998877661L;
    private static final long UNINSTALLED_ID = 998877662L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StoreRepository stores;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        cleanUp();
        Store active = new Store();
        active.setStoreId(ACTIVE_ID);
        active.setStoreName("Teste Menu Ativa");
        stores.save(active);

        Store uninstalled = new Store();
        uninstalled.setStoreId(UNINSTALLED_ID);
        uninstalled.setStoreName("Teste Menu Desinstalada");
        uninstalled.setUninstalledAt(Instant.parse("2026-09-28T12:00:00Z"));
        stores.save(uninstalled);

        session = new MockHttpSession();
        session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
    }

    @AfterEach
    void cleanUp() {
        stores.findByStoreId(ACTIVE_ID).ifPresent(stores::delete);
        stores.findByStoreId(UNINSTALLED_ID).ifPresent(stores::delete);
    }

    @Test
    void menuShowsOnlyUninstalledStoresAndKeepsFilterWhenSearching() throws Exception {
        mockMvc.perform(get("/backoffice/stores")
                        .param("status", "uninstalled")
                        .param("q", "Teste Menu")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Teste Menu Desinstalada")))
                .andExpect(content().string(not(containsString("Teste Menu Ativa"))))
                .andExpect(content().string(containsString("name=\"status\" value=\"uninstalled\"")))
                .andExpect(content().string(containsString("28/09/2026")));
    }

    @Test
    void menuRequiresBackofficeSession() throws Exception {
        mockMvc.perform(get("/backoffice/stores").param("status", "uninstalled"))
                .andExpect(redirectedUrl("/backoffice/login"));
    }
}
