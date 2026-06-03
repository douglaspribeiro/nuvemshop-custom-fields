package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

@Service
public class ScriptInstallService {

    private final NuvemshopApiClient apiClient;
    private final NuvemshopProperties properties;

    public ScriptInstallService(NuvemshopApiClient apiClient, NuvemshopProperties properties) {
        this.apiClient = apiClient;
        this.properties = properties;
    }

    public String personalizerScriptSrc(Store store) {
        return properties.appBaseUrl() + "/assets/nuvemshop-personalizer.js?store=" + store.getStoreId();
    }

    public void installPersonalizerScript(Store store) {
        JsonNode scripts = apiClient.listScripts(store);
        String expectedSrc = personalizerScriptSrc(store);
        if (hasScript(scripts, expectedSrc)) {
            return;
        }
        apiClient.createScript(store, expectedSrc);
    }

    public void removePersonalizerScripts(Store store) {
        JsonNode scripts = apiClient.listScripts(store);
        if (scripts == null || !scripts.isArray()) {
            return;
        }
        String expectedSrc = personalizerScriptSrc(store);
        for (JsonNode script : scripts) {
            if (expectedSrc.equals(script.path("src").asText())) {
                apiClient.deleteScript(store, script.path("id").asLong());
            }
        }
    }

    private boolean hasScript(JsonNode scripts, String expectedSrc) {
        if (scripts == null || !scripts.isArray()) {
            return false;
        }
        for (JsonNode script : scripts) {
            if (expectedSrc.equals(script.path("src").asText())) {
                return true;
            }
        }
        return false;
    }
}
