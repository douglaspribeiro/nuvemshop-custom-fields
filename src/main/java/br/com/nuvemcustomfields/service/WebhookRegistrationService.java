package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Set;

@Service
public class WebhookRegistrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookRegistrationService.class);

    private static final Set<String> REQUIRED_EVENTS = Set.of(
            "app/uninstalled",
            "product/deleted",
            "subscription/updated",
            "app/suspended",
            "app/resumed"
    );

    private final NuvemshopApiClient apiClient;
    private final NuvemshopProperties properties;

    public WebhookRegistrationService(NuvemshopApiClient apiClient, NuvemshopProperties properties) {
        this.apiClient = apiClient;
        this.properties = properties;
    }

    public void registerRequiredWebhooks(Store store) {
        LOGGER.info("webhook.registration.start store_id={}", store.getStoreId());
        if (!isPublicHttps(properties.appBaseUrl())) {
            LOGGER.warn(
                    "webhook.registration.skip store_id={} reason=app_base_url_not_public_https app_base_url={}",
                    store.getStoreId(),
                    properties.appBaseUrl()
            );
            return;
        }
        JsonNode existing = apiClient.listWebhooks(store);
        for (String event : REQUIRED_EVENTS) {
            if (!hasWebhook(existing, event)) {
                LOGGER.info("webhook.registration.create store_id={} event={}", store.getStoreId(), event);
                apiClient.createWebhook(store, event, properties.appBaseUrl() + "/webhooks/nuvemshop");
            } else {
                LOGGER.info("webhook.registration.exists store_id={} event={}", store.getStoreId(), event);
            }
        }
        LOGGER.info("webhook.registration.done store_id={}", store.getStoreId());
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
