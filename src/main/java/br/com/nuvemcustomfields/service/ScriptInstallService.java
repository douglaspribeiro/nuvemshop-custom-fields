package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.ScriptDiagnostics;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ScriptInstallService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptInstallService.class);

    private final NuvemshopApiClient apiClient;
    private final NuvemshopProperties properties;
    private final IntegrationLogService integrationLogService;

    public ScriptInstallService(
            NuvemshopApiClient apiClient,
            NuvemshopProperties properties,
            IntegrationLogService integrationLogService
    ) {
        this.apiClient = apiClient;
        this.properties = properties;
        this.integrationLogService = integrationLogService;
    }

    /**
     * O script legado de DOM foi removido: o app e SDK-only. Este src permanece apenas para
     * a limpeza de associacoes antigas na desinstalacao, para nao deixar script orfao na loja.
     */
    private String legacyPersonalizerScriptSrc(Store store) {
        return properties.appBaseUrl() + "/assets/nuvemshop-personalizer.js?store=" + store.getStoreId();
    }

    public String checkoutScriptSrc() {
        String src = properties.appBaseUrl() + "/assets/nuvemshop-checkout.js";
        LOGGER.info("script.checkout.src src={}", src);
        return src;
    }

    public String storefrontSdkScriptSrc() {
        String src = properties.appBaseUrl() + "/assets/nuvemshop-storefront-sdk.js";
        LOGGER.info("script.storefront_sdk.src src={}", src);
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
        Set<String> expectedSrcs = expectedScriptSrcs(store);
        Set<Long> scriptIds = configuredScriptIds();
        if (scriptIds.isEmpty()) {
            LOGGER.warn(
                    "script.install.skip store_id={} reason=script_id_not_configured expected_srcs={}",
                    store.getStoreId(),
                    expectedSrcs
            );
            return;
        }
        LOGGER.info("script.install.configured store_id={} script_ids={}", store.getStoreId(), scriptIds);
        JsonNode scripts = apiClient.listScripts(store);
        int installed = 0;
        int failed = 0;
        for (Long scriptId : scriptIds) {
            if (hasScriptId(scripts, scriptId)) {
                LOGGER.info("script.install.exists store_id={} script_id={}", store.getStoreId(), scriptId);
                continue;
            }
            // Isolado de proposito: um script_id obsoleto (removido no Partner Portal)
            // nao pode impedir a instalacao dos outros.
            try {
                apiClient.createScript(store, scriptId);
                installed++;
                LOGGER.info("script.install.done store_id={} script_id={}", store.getStoreId(), scriptId);
            } catch (RuntimeException ex) {
                failed++;
                LOGGER.error(
                        "script.install.script_failed store_id={} script_id={} message={}",
                        store.getStoreId(),
                        scriptId,
                        ex.getMessage()
                );
                integrationLogService.warn(
                        store.getStoreId(),
                        "script.install.script_failed",
                        "Falha ao associar script " + scriptId + " a loja: " + ex.getMessage()
                );
            }
        }
        LOGGER.info(
                "script.install.summary store_id={} configured={} installed={} failed={}",
                store.getStoreId(),
                scriptIds.size(),
                installed,
                failed
        );
    }

    /**
     * Le a loja na API e cruza com os ids configurados. Nunca lanca: um erro de API tambem
     * e diagnostico, e a tela do backoffice precisa renderizar de qualquer forma.
     */
    public ScriptDiagnostics diagnose(Store store) {
        List<Long> configured = List.copyOf(configuredScriptIds());
        if (store.getAccessToken() == null || store.getAccessToken().isBlank()) {
            return ScriptDiagnostics.failed(configured, "Loja sem access token: desinstalada ou nunca conectada.");
        }

        JsonNode scripts;
        try {
            scripts = apiClient.listScripts(store);
        } catch (RuntimeException ex) {
            LOGGER.warn("script.diagnose.failed store_id={} message={}", store.getStoreId(), ex.getMessage());
            return ScriptDiagnostics.failed(configured, "Falha ao consultar a API: " + ex.getMessage());
        }

        List<ScriptDiagnostics.InstalledScript> installed = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (JsonNode script : iterableScripts(scripts)) {
            long id = script.path("id").asLong(-1);
            seen.add(id);
            String src = script.path("src").asText("");
            if (src.isBlank()) {
                src = script.path("current_version").path("src").asText("");
            }
            installed.add(new ScriptDiagnostics.InstalledScript(
                    id,
                    script.path("name").asText(""),
                    script.path("status").asText(""),
                    script.path("location").asText(""),
                    script.path("event").asText(""),
                    script.path("is_auto_install").asBoolean(false),
                    src,
                    configured.contains(id),
                    versionLabel(script.path("current_version")),
                    versionLabel(script.path("draft_version"))
            ));
        }

        List<Long> missing = configured.stream().filter(id -> !seen.contains(id)).toList();
        LOGGER.info(
                "script.diagnose.done store_id={} configured={} installed={} missing={}",
                store.getStoreId(),
                configured.size(),
                installed.size(),
                missing
        );
        return new ScriptDiagnostics(configured, installed, missing, null);
    }

    /** `version` e o rotulo humano; cai para o `id` da versao quando ausente. */
    private String versionLabel(JsonNode versionNode) {
        if (versionNode == null || versionNode.isMissingNode() || versionNode.isNull()) {
            return null;
        }
        String version = versionNode.path("version").asText("");
        if (!version.isBlank()) {
            return version;
        }
        String id = versionNode.path("id").asText("");
        return id.isBlank() ? null : id;
    }

    private Set<Long> configuredScriptIds() {
        Set<Long> scriptIds = new LinkedHashSet<>();
        addScriptId(scriptIds, "checkout_script_id", properties.checkoutScriptId());
        addScriptId(scriptIds, "storefront_sdk_script_id", properties.storefrontSdkScriptId());
        return scriptIds;
    }

    private void addScriptId(Set<Long> scriptIds, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            scriptIds.add(Long.valueOf(value));
        } catch (NumberFormatException ex) {
            LOGGER.warn("script.install.invalid_script_id name={} value={}", name, value);
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
        Set<String> expectedSrcs = expectedScriptSrcs(store);
        Set<Long> expectedScriptIds = configuredScriptIds();
        for (JsonNode script : iterableScripts(scripts)) {
            if (!matchesScript(script, expectedSrcs, expectedScriptIds)) {
                continue;
            }
            long scriptId = script.path("id").asLong();
            LOGGER.info("script.remove.delete store_id={} script_id={}", store.getStoreId(), scriptId);
            try {
                apiClient.deleteScript(store, scriptId);
            } catch (RuntimeException ex) {
                LOGGER.error(
                        "script.remove.script_failed store_id={} script_id={} message={}",
                        store.getStoreId(),
                        scriptId,
                        ex.getMessage()
                );
            }
        }
        LOGGER.info("script.remove.done store_id={}", store.getStoreId());
    }

    private boolean hasScriptId(JsonNode scripts, Long expectedScriptId) {
        for (JsonNode script : iterableScripts(scripts)) {
            if (script.path("id").asLong(-1) == expectedScriptId) {
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

    public Set<String> expectedScriptSrcs(Store store) {
        Set<String> expectedSrcs = new LinkedHashSet<>();
        expectedSrcs.add(legacyPersonalizerScriptSrc(store));
        expectedSrcs.add(checkoutScriptSrc());
        expectedSrcs.add(storefrontSdkScriptSrc());
        return expectedSrcs;
    }

    private boolean matchesScript(JsonNode script, Set<String> expectedSrcs, Set<Long> expectedScriptIds) {
        if (expectedScriptIds.contains(script.path("id").asLong(-1))) {
            return true;
        }
        String src = script.path("src").asText();
        if (src.isBlank()) {
            src = script.path("current_version").path("src").asText();
        }
        return expectedSrcs.contains(src);
    }
}
