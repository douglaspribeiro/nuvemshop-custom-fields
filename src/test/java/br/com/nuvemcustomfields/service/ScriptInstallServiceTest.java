package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScriptInstallServiceTest {

    private final NuvemshopApiClient apiClient = mock(NuvemshopApiClient.class);

    @Test
    void installsStorefrontAndCheckoutScriptsWhenBothIdsAreConfigured() {
        Store store = store();
        when(apiClient.listScripts(store)).thenReturn(new ObjectMapper().createArrayNode());

        ScriptInstallService service = new ScriptInstallService(
                apiClient,
                properties("7100", "7200")
        );

        service.installPersonalizerScript(store);

        verify(apiClient).createScript(store, 7100L);
        verify(apiClient).createScript(store, 7200L);
    }

    @Test
    void doesNotTreatSameSourceAsInstalledWhenCheckoutScriptIdIsMissing() {
        Store store = store();
        ArrayNode scripts = new ObjectMapper().createArrayNode();
        scripts.addObject()
                .put("id", 7100L)
                .put("src", "https://app.example.com/assets/nuvemshop-personalizer.js?store=123");
        when(apiClient.listScripts(store)).thenReturn(scripts);

        ScriptInstallService service = new ScriptInstallService(
                apiClient,
                properties("7100", "7200")
        );

        service.installPersonalizerScript(store);

        verify(apiClient, never()).createScript(store, 7100L);
        verify(apiClient).createScript(store, 7200L);
    }

    @Test
    void removesScriptsByConfiguredIdsAndLegacySource() {
        Store store = store();
        ArrayNode scripts = new ObjectMapper().createArrayNode();
        scripts.addObject().put("id", 7100L);
        scripts.addObject().put("id", 7200L);
        scripts.addObject()
                .put("id", 7300L)
                .put("src", "https://app.example.com/assets/nuvemshop-personalizer.js?store=123");
        when(apiClient.listScripts(store)).thenReturn(scripts);

        ScriptInstallService service = new ScriptInstallService(
                apiClient,
                properties("7100", "7200")
        );

        service.removePersonalizerScripts(store);

        verify(apiClient).deleteScript(store, 7100L);
        verify(apiClient).deleteScript(store, 7200L);
        verify(apiClient).deleteScript(store, 7300L);
    }

    private Store store() {
        Store store = new Store();
        store.setStoreId(123L);
        store.setAccessToken("token");
        return store;
    }

    private NuvemshopProperties properties(String scriptId, String checkoutScriptId) {
        return new NuvemshopProperties(
                "client",
                "secret",
                "https://app.example.com/oauth/callback",
                "https://example.com/{clientId}/authorize",
                "https://example.com/token",
                "https://api.example.com",
                "https://app.example.com",
                "read_products",
                "tests",
                scriptId,
                checkoutScriptId
        );
    }
}
