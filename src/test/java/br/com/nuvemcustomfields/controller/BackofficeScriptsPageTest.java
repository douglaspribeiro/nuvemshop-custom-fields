package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.NuvemshopApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renderiza a tela de scripts de verdade: erro de template Thymeleaf so aparece em runtime,
 * e teste de service nao cobre isso.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BackofficeScriptsPageTest {

    private static final long STORE_ID = 987654L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StoreRepository storeRepository;

    @MockitoBean
    private NuvemshopApiClient apiClient;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        storeRepository.findByStoreId(STORE_ID).ifPresent(storeRepository::delete);
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setStoreName("Loja de Teste");
        store.setAccessToken("token");
        storeRepository.save(store);

        session = new MockHttpSession();
        session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
    }

    @Test
    void rendersScriptsTableAndFlagsOrphanAndInactiveScripts() throws Exception {
        ArrayNode scripts = new ObjectMapper().createArrayNode();
        scripts.addObject()
                .put("id", 4242L)
                .put("name", "Storefront SDK")
                .put("status", "draft")
                .put("location", "store")
                .put("event", "onload")
                .put("is_auto_install", false);
        when(apiClient.listScripts(org.mockito.ArgumentMatchers.any(Store.class))).thenReturn(scripts);

        mockMvc.perform(get("/backoffice/stores/{storeId}/scripts", STORE_ID).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Storefront SDK")))
                .andExpect(content().string(containsString("draft")))
                // id nao configurado no app aparece como orfao
                .andExpect(content().string(containsString("orfao")))
                .andExpect(content().string(containsString("Loja de Teste")));
    }

    @Test
    void rendersApiErrorWithoutBreakingThePage() throws Exception {
        when(apiClient.listScripts(org.mockito.ArgumentMatchers.any(Store.class)))
                .thenThrow(new IllegalStateException("401 Unauthorized"));

        mockMvc.perform(get("/backoffice/stores/{storeId}/scripts", STORE_ID).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("401 Unauthorized")))
                .andExpect(content().string(not(containsString("Tudo associado"))));
    }
}
