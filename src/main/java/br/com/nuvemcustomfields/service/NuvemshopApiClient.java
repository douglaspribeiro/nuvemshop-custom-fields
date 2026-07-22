package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.ProductSummary;
import br.com.nuvemcustomfields.dto.StoreProfile;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class NuvemshopApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(NuvemshopApiClient.class);

    private final NuvemshopProperties properties;
    private final RestClient restClient;

    public NuvemshopApiClient(NuvemshopProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.defaultHeader("User-Agent", properties.userAgent()).build();
    }

    public List<ProductSummary> listProducts(Store store) {
        LOGGER.info("nuvemshop.api.list_products.start store_id={}", store.getStoreId());
        JsonNode response;
        try {
            response = restClient.get()
                    .uri(properties.apiBaseUrl() + "/v1/{storeId}/products", store.getStoreId())
                    .header("Authentication", "bearer " + store.getAccessToken())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException ex) {
            logRestFailure("nuvemshop.api.list_products.error", store.getStoreId(), ex);
            throw ex;
        }

        List<ProductSummary> products = new ArrayList<>();
        if (response == null || !response.isArray()) {
            LOGGER.warn("nuvemshop.api.list_products.unexpected_response store_id={} response_present={}", store.getStoreId(), response != null);
            return products;
        }
        for (JsonNode product : response) {
            products.add(new ProductSummary(product.path("id").asLong(), localizedName(product.path("name"), "Produto sem nome")));
        }
        LOGGER.info("nuvemshop.api.list_products.done store_id={} products_count={}", store.getStoreId(), products.size());
        return products;
    }

    public String getStoreName(Store store) {
        return getStoreProfile(store).name();
    }

    public StoreProfile getStoreProfile(Store store) {
        LOGGER.info("nuvemshop.api.get_store.start store_id={}", store.getStoreId());
        try {
            JsonNode response = restClient.get()
                    .uri(properties.apiBaseUrl() + "/v1/{storeId}/store?fields=name,country,main_currency", store.getStoreId())
                    .header("Authentication", "bearer " + store.getAccessToken())
                    .retrieve()
                    .body(JsonNode.class);
            String name = localizedName(response == null ? null : response.path("name"), "Loja sem nome");
            String countryCode = normalizeCountry(countryCode(response));
            String currency = normalizeCurrency(firstText(response, "main_currency", "currency"));
            if ((currency == null || currency.isBlank()) && countryCode != null) {
                currency = currencyForCountry(countryCode);
            }
            LOGGER.info(
                    "nuvemshop.api.get_store.done store_id={} store_name_present={} country={} currency={}",
                    store.getStoreId(),
                    !name.isBlank(),
                    countryCode,
                    currency
            );
            return new StoreProfile(name, countryCode, currency);
        } catch (RuntimeException ex) {
            logRestFailure("nuvemshop.api.get_store.error", store.getStoreId(), ex);
            throw ex;
        }
    }

    public JsonNode listWebhooks(Store store) {
        LOGGER.info("nuvemshop.api.list_webhooks.start store_id={}", store.getStoreId());
        try {
            JsonNode response = restClient.get()
                    .uri(properties.apiBaseUrl() + "/v1/{storeId}/webhooks", store.getStoreId())
                    .header("Authentication", "bearer " + store.getAccessToken())
                    .retrieve()
                    .body(JsonNode.class);
            LOGGER.info("nuvemshop.api.list_webhooks.done store_id={} response_present={}", store.getStoreId(), response != null);
            return response;
        } catch (RuntimeException ex) {
            logRestFailure("nuvemshop.api.list_webhooks.error", store.getStoreId(), ex);
            throw ex;
        }
    }

    public void createWebhook(Store store, String event, String url) {
        LOGGER.info("nuvemshop.api.create_webhook.start store_id={} event={} url={}", store.getStoreId(), event, url);
        try {
            restClient.post()
                    .uri(properties.apiBaseUrl() + "/v1/{storeId}/webhooks", store.getStoreId())
                    .header("Authentication", "bearer " + store.getAccessToken())
                    .body(Map.of("event", event, "url", url))
                    .retrieve()
                    .toBodilessEntity();
            LOGGER.info("nuvemshop.api.create_webhook.done store_id={} event={}", store.getStoreId(), event);
        } catch (RuntimeException ex) {
            logRestFailure("nuvemshop.api.create_webhook.error", store.getStoreId(), ex);
            throw ex;
        }
    }

    public JsonNode listScripts(Store store) {
        LOGGER.info("nuvemshop.api.list_scripts.start store_id={}", store.getStoreId());
        try {
            JsonNode response = restClient.get()
                    .uri(properties.apiBaseUrl() + "/v1/{storeId}/scripts", store.getStoreId())
                    .header("Authentication", "bearer " + store.getAccessToken())
                    .retrieve()
                    .body(JsonNode.class);
            LOGGER.info("nuvemshop.api.list_scripts.done store_id={} response_present={}", store.getStoreId(), response != null);
            return response;
        } catch (RuntimeException ex) {
            logRestFailure("nuvemshop.api.list_scripts.error", store.getStoreId(), ex);
            throw ex;
        }
    }

    public void deleteScript(Store store, Long scriptId) {
        LOGGER.info("nuvemshop.api.delete_script.start store_id={} script_id={}", store.getStoreId(), scriptId);
        try {
            restClient.delete()
                    .uri(properties.apiBaseUrl() + "/v1/{storeId}/scripts/{scriptId}", store.getStoreId(), scriptId)
                    .header("Authentication", "bearer " + store.getAccessToken())
                    .retrieve()
                    .toBodilessEntity();
            LOGGER.info("nuvemshop.api.delete_script.done store_id={} script_id={}", store.getStoreId(), scriptId);
        } catch (RuntimeException ex) {
            logRestFailure("nuvemshop.api.delete_script.error", store.getStoreId(), ex);
            throw ex;
        }
    }

    public void createScript(Store store, Long scriptId) {
        LOGGER.info("nuvemshop.api.create_script.start store_id={} script_id={}", store.getStoreId(), scriptId);
        try {
            restClient.post()
                    .uri(properties.apiBaseUrl() + "/v1/{storeId}/scripts", store.getStoreId())
                    .header("Authentication", "bearer " + store.getAccessToken())
                    .body(Map.of("script_id", scriptId, "query_params", "{\"store\":\"" + store.getStoreId() + "\"}"))
                    .retrieve()
                    .toBodilessEntity();
            LOGGER.info("nuvemshop.api.create_script.done store_id={}", store.getStoreId());
        } catch (RuntimeException ex) {
            logRestFailure("nuvemshop.api.create_script.error", store.getStoreId(), ex);
            throw ex;
        }
    }

    public JsonNode listRecentOrders(Store store) {
        LOGGER.info("nuvemshop.api.list_recent_orders.start store_id={}", store.getStoreId());
        try {
            JsonNode response = restClient.get()
                    .uri(properties.apiBaseUrl() + "/v1/{storeId}/orders?per_page=50", store.getStoreId())
                    .header("Authentication", "bearer " + store.getAccessToken())
                    .retrieve()
                    .body(JsonNode.class);
            LOGGER.info("nuvemshop.api.list_recent_orders.done store_id={} response_present={}", store.getStoreId(), response != null);
            return response;
        } catch (RuntimeException ex) {
            logRestFailure("nuvemshop.api.list_recent_orders.error", store.getStoreId(), ex);
            throw ex;
        }
    }

    private void logRestFailure(String event, Long storeId, RuntimeException ex) {
        if (ex instanceof RestClientResponseException responseException) {
            LOGGER.error(
                    "{} store_id={} status={} response_body={}",
                    event,
                    storeId,
                    responseException.getStatusCode(),
                    truncate(responseException.getResponseBodyAsString()),
                    ex
            );
            return;
        }
        LOGGER.error("{} store_id={} message={}", event, storeId, ex.getMessage(), ex);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private String localizedName(JsonNode nameNode, String fallback) {
        if (nameNode == null || nameNode.isMissingNode() || nameNode.isNull()) {
            return fallback;
        }
        if (nameNode.isTextual()) {
            return nameNode.asText();
        }
        String portuguese = nameNode.path("pt").asText(null);
        if (portuguese != null && !portuguese.isBlank()) {
            return portuguese;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = nameNode.fields();
        while (fields.hasNext()) {
            String value = fields.next().getValue().asText();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private String countryCode(JsonNode response) {
        String country = firstText(response, "country_code", "country", "main_country");
        if (country != null && !country.isBlank()) {
            return country;
        }
        JsonNode countryNode = response == null ? null : response.path("country");
        country = firstText(countryNode, "code", "iso_code", "country_code");
        if (country != null && !country.isBlank()) {
            return country;
        }
        String locale = firstText(response, "locale");
        if (locale != null && locale.contains("_")) {
            return locale.substring(locale.lastIndexOf('_') + 1);
        }
        if (locale != null && locale.contains("-")) {
            return locale.substring(locale.lastIndexOf('-') + 1);
        }
        return null;
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            String text = textValue(value);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private String textValue(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isObject()) {
            String code = firstText(value, "code", "iso_code", "currency", "id");
            if (code != null && !code.isBlank()) {
                return code;
            }
        }
        return value.asText(null);
    }

    private String normalizeCountry(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip().toUpperCase();
        return normalized.length() > 2 ? normalized.substring(0, 2) : normalized;
    }

    private String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip().toUpperCase();
        return normalized.length() > 3 ? normalized.substring(0, 3) : normalized;
    }

    private String currencyForCountry(String countryCode) {
        return switch (countryCode) {
            case "BR" -> "BRL";
            case "AR" -> "ARS";
            case "CL" -> "CLP";
            case "MX" -> "MXN";
            case "CO" -> "COP";
            default -> null;
        };
    }
}
