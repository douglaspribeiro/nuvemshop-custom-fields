package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScriptInstallServiceTest {

    private final NuvemshopApiClient apiClient = mock(NuvemshopApiClient.class);
    private final IntegrationLogService integrationLogService = mock(IntegrationLogService.class);

    /**
     * Regressao: um script_id obsoleto (removido no Partner Portal) fazia o createScript
     * lancar e abortar o laco, deixando os scripts seguintes sem instalar.
     */
    @Test
    void keepsInstallingRemainingScriptsWhenOneScriptIdIsStale() {
        Store store = store();
        when(apiClient.listScripts(store)).thenReturn(new ObjectMapper().createArrayNode());
        doThrow(new IllegalStateException("404 Not Found"))
                .when(apiClient).createScript(store, 7100L);

        ScriptInstallService service = new ScriptInstallService(
                apiClient,
                properties("7100", "7200", "7500"),
                integrationLogService
        );

        service.installPersonalizerScript(store);

        verify(apiClient).createScript(store, 7100L);
        verify(apiClient).createScript(store, 7200L);
        verify(apiClient).createScript(store, 7500L);
        verify(integrationLogService).warn(eq(123L), eq("script.install.script_failed"), anyString());
    }

    @Test
    void installsStorefrontAndCheckoutScriptsWhenBothIdsAreConfigured() {
        Store store = store();
        when(apiClient.listScripts(store)).thenReturn(new ObjectMapper().createArrayNode());

        ScriptInstallService service = new ScriptInstallService(
                apiClient,
                properties("7100", "7200", "7500"),
                integrationLogService
        );

        service.installPersonalizerScript(store);

        verify(apiClient).createScript(store, 7100L);
        verify(apiClient).createScript(store, 7200L);
        verify(apiClient).createScript(store, 7500L);
    }

    @Test
    void doesNotTreatStorefrontSourceAsInstalledCheckoutScript() {
        Store store = store();
        ArrayNode scripts = new ObjectMapper().createArrayNode();
        scripts.addObject()
                .put("id", 7100L)
                .put("src", "https://app.example.com/assets/nuvemshop-personalizer.js?store=123");
        when(apiClient.listScripts(store)).thenReturn(scripts);

        ScriptInstallService service = new ScriptInstallService(
                apiClient,
                properties("7100", "7200", "7500"),
                integrationLogService
        );

        service.installPersonalizerScript(store);

        verify(apiClient, never()).createScript(store, 7100L);
        verify(apiClient).createScript(store, 7200L);
        verify(apiClient).createScript(store, 7500L);
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
        scripts.addObject()
                .put("id", 7400L)
                .put("src", "https://app.example.com/assets/nuvemshop-checkout.js");
        scripts.addObject()
                .put("id", 7600L)
                .put("src", "https://app.example.com/assets/nuvemshop-storefront-sdk.js");
        when(apiClient.listScripts(store)).thenReturn(scripts);

        ScriptInstallService service = new ScriptInstallService(
                apiClient,
                properties("7100", "7200", "7500"),
                integrationLogService
        );

        service.removePersonalizerScripts(store);

        verify(apiClient).deleteScript(store, 7100L);
        verify(apiClient).deleteScript(store, 7200L);
        verify(apiClient).deleteScript(store, 7300L);
        verify(apiClient).deleteScript(store, 7400L);
        verify(apiClient).deleteScript(store, 7600L);
    }

    private Store store() {
        Store store = new Store();
        store.setStoreId(123L);
        store.setAccessToken("token");
        return store;
    }

    private NuvemshopProperties properties(String scriptId, String checkoutScriptId, String storefrontSdkScriptId) {
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
                checkoutScriptId,
                storefrontSdkScriptId
        );
    }
}
