package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.IntegrationLogRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScriptEventBeaconTest {

    private static final long STORE_ID = 5544332211L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private IntegrationLogRepository integrationLogRepository;

    @BeforeEach
    void setUp() {
        // integration_logs nao tem FK para stores, entao apagar a loja nao limpa os logs:
        // sem isto os metodos vazam estado entre si no H2 compartilhado.
        integrationLogRepository.deleteAll(
                integrationLogRepository.findTop20ByStoreIdOrderByCreatedAtDesc(STORE_ID)
        );
        storeRepository.findByStoreId(STORE_ID).ifPresent(storeRepository::delete);
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setAccessToken("token");
        storeRepository.save(store);
    }

    @Test
    void storefrontBeaconBecomesAnIntegrationLogVisibleInBackoffice() throws Exception {
        mockMvc.perform(get("/public/script-events")
                        .param("event", "storefront_sdk")
                        .param("storeId", String.valueOf(STORE_ID))
                        .param("productId", "348550655")
                        .param("reason", "rendered")
                        .param("path", "page:loaded|cart:update"))
                .andExpect(status().isOk());

        var logs = integrationLogRepository.findTop20ByStoreIdOrderByCreatedAtDesc(STORE_ID);
        assertThat(logs).isNotEmpty();
        assertThat(logs.getFirst().getEventType()).isEqualTo("storefront.sdk.rendered");
        assertThat(logs.getFirst().getMessage()).contains("348550655").contains("page:loaded|cart:update");
    }

    @Test
    void sanitizesHostileReasonAndDetailBeforePersisting() throws Exception {
        mockMvc.perform(get("/public/script-events")
                        .param("event", "storefront_sdk")
                        .param("storeId", String.valueOf(STORE_ID))
                        .param("reason", "<script>alert(1)</script>")
                        .param("path", "a b<>&c"))
                .andExpect(status().isOk());

        var log = integrationLogRepository.findTop20ByStoreIdOrderByCreatedAtDesc(STORE_ID).getFirst();
        assertThat(log.getEventType()).doesNotContain("<").doesNotContain(">");
        assertThat(log.getMessage()).doesNotContain("<").doesNotContain(">");
    }

    @Test
    void doesNotPersistUnknownEventNames() throws Exception {
        mockMvc.perform(get("/public/script-events")
                        .param("event", "qualquer_outra_coisa")
                        .param("storeId", String.valueOf(STORE_ID))
                        .param("reason", "spam"))
                .andExpect(status().isOk());

        assertThat(integrationLogRepository.findTop20ByStoreIdOrderByCreatedAtDesc(STORE_ID)).isEmpty();
    }
}
