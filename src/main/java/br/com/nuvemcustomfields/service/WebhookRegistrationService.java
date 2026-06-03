package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class WebhookRegistrationService {

    private static final Set<String> REQUIRED_EVENTS = Set.of("app/uninstalled", "product/deleted");

    private final NuvemshopApiClient apiClient;
    private final NuvemshopProperties properties;

    public WebhookRegistrationService(NuvemshopApiClient apiClient, NuvemshopProperties properties) {
        this.apiClient = apiClient;
        this.properties = properties;
    }

    public void registerRequiredWebhooks(Store store) {
        JsonNode existing = apiClient.listWebhooks(store);
        for (String event : REQUIRED_EVENTS) {
            if (!hasWebhook(existing, event)) {
                apiClient.createWebhook(store, event, properties.appBaseUrl() + "/webhooks/nuvemshop");
            }
        }
    }

    private boolean hasWebhook(JsonNode existing, String event) {
        if (existing == null || !existing.isArray()) {
            return false;
        }
        for (JsonNode webhook : existing) {
            if (event.equals(webhook.path("event").asText())) {
                return true;
            }
        }
        return false;
    }
}
