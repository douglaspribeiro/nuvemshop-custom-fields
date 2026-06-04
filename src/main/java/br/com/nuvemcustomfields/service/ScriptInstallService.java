package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;

@Service
public class ScriptInstallService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptInstallService.class);

    private final NuvemshopApiClient apiClient;
    private final NuvemshopProperties properties;

    public ScriptInstallService(NuvemshopApiClient apiClient, NuvemshopProperties properties) {
        this.apiClient = apiClient;
        this.properties = properties;
    }

    public String personalizerScriptSrc(Store store) {
        String src = properties.appBaseUrl() + "/assets/nuvemshop-personalizer.js?store=" + store.getStoreId();
        LOGGER.info("script.personalizer.src store_id={} src={}", store.getStoreId(), src);
        return src;
    }

    public void installPersonalizerScript(Store store) {
        LOGGER.info("script.install.start store_id={}", store.getStoreId());
        if (!isPublicHttps(properties.appBaseUrl())) {
            LOGGER.warn(
                    "script.install.skip store_id={} reason=app_base_url_not_public_https app_base_url={}",
                    store.getStoreId(),
                    properties.appBaseUrl()
            );
            return;
        }
        String expectedSrc = personalizerScriptSrc(store);
        Long scriptId = scriptId();
        if (scriptId == null) {
            LOGGER.warn(
                    "script.install.skip store_id={} reason=script_id_not_configured expected_src={}",
                    store.getStoreId(),
                    expectedSrc
            );
            return;
        }
        JsonNode scripts = apiClient.listScripts(store);
        if (hasScript(scripts, expectedSrc, scriptId)) {
            LOGGER.info("script.install.exists store_id={}", store.getStoreId());
            return;
        }
        apiClient.createScript(store, scriptId);
        LOGGER.info("script.install.done store_id={}", store.getStoreId());
    }

    private Long scriptId() {
        String value = properties.scriptId();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            LOGGER.warn("script.install.invalid_script_id script_id={}", value);
            return null;
        }
    }

    private boolean isPublicHttps(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && !"localhost".equalsIgnoreCase(host)
                    && !"127.0.0.1".equals(host)
                    && !"0.0.0.0".equals(host);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public void removePersonalizerScripts(Store store) {
        LOGGER.info("script.remove.start store_id={}", store.getStoreId());
        JsonNode scripts = apiClient.listScripts(store);
        if (scripts == null || (!scripts.isArray() && !scripts.path("result").isArray())) {
            LOGGER.warn("script.remove.unexpected_response store_id={} response_present={}", store.getStoreId(), scripts != null);
            return;
        }
        String expectedSrc = personalizerScriptSrc(store);
        Long expectedScriptId = scriptId();
        for (JsonNode script : iterableScripts(scripts)) {
            if (matchesScript(script, expectedSrc, expectedScriptId)) {
                LOGGER.info("script.remove.delete store_id={} script_id={}", store.getStoreId(), script.path("id").asLong());
                apiClient.deleteScript(store, script.path("id").asLong());
            }
        }
        LOGGER.info("script.remove.done store_id={}", store.getStoreId());
    }

    private boolean hasScript(JsonNode scripts, String expectedSrc, Long expectedScriptId) {
        for (JsonNode script : iterableScripts(scripts)) {
            if (matchesScript(script, expectedSrc, expectedScriptId)) {
                return true;
            }
        }
        return false;
    }

    private Iterable<JsonNode> iterableScripts(JsonNode scripts) {
        if (scripts == null) {
            return java.util.List.of();
        }
        if (scripts.isArray()) {
            return scripts;
        }
        JsonNode result = scripts.path("result");
        if (result.isArray()) {
            return result;
        }
        return java.util.List.of();
    }

    private boolean matchesScript(JsonNode script, String expectedSrc, Long expectedScriptId) {
        if (expectedScriptId != null && script.path("id").asLong(-1) == expectedScriptId) {
            return true;
        }
        String src = script.path("src").asText();
        if (src.isBlank()) {
            src = script.path("current_version").path("src").asText();
        }
        return expectedSrc.equals(src);
    }
}
