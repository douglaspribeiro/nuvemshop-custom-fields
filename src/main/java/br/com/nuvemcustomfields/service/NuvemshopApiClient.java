package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.ProductPage;
import br.com.nuvemcustomfields.dto.ProductSummary;
import br.com.nuvemcustomfields.dto.StoreProfile;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class NuvemshopApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(NuvemshopApiClient.class);

    public static final int DEFAULT_PER_PAGE = 50;
    private static final int MAX_PER_PAGE = 200;

    /** Teto de paginas ao varrer colecoes pequenas (scripts/webhooks); evita loop infinito se a API repetir paginas. */
    private static final int MAX_PAGES_SWEEP = 20;

    private final NuvemshopProperties properties;
    private final RestClient restClient;

    public NuvemshopApiClient(NuvemshopProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.defaultHeader("User-Agent", properties.userAgent()).build();
    }

    public ProductPage listProducts(Store store, int page, int perPage, String query) {
        int safePage = Math.max(page, 1);
        int safePerPage = Math.clamp(perPage, 1, MAX_PER_PAGE);
        String safeQuery = (query == null || query.isBlank()) ? null : query.strip();

        LOGGER.info(
                "nuvemshop.api.list_products.start store_id={} page={} per_page={} query_present={}",
                store.getStoreId(),
                safePage,
                safePerPage,
                safeQuery != null
        );

        // toUri() em vez de toUriString(): RestClient re-encoda Strings e o `q` sairia com %2520.
        URI uri = UriComponentsBuilder.fromUriString(properties.apiBaseUrl())
                .path("/v1/{storeId}/products")
                .queryParam("page", safePage)
                .queryParam("per_page", safePerPage)
                .queryParam("fields", "id,name")
                .queryParamIfPresent("q", Optional.ofNullable(safeQuery))
                .encode()
                .buildAndExpand(store.getStoreId())
                .toUri();

        ResponseEntity<JsonNode> response;
        try {
            response = restClient.get()
                    .uri(uri)
                    .header("Authentication", "bearer " + store.getAccessToken())
                    .retrieve()
                    .toEntity(JsonNode.class);
        } catch (RuntimeException ex) {
            logRestFailure("nuvemshop.api.list_products.error", store.getStoreId(), ex);
            throw ex;
        }

        JsonNode body = arrayBody(response.getBody());
        if (body == null) {
            LOGGER.warn(
                    "nuvemshop.api.list_products.unexpected_response store_id={} page={} response_present={}",
                    store.getStoreId(),
                    safePage,
                    response.getBody() != null
            );
            return ProductPage.empty(safePage, safePerPage, safeQuery);
        }

        List<ProductSummary> products = new ArrayList<>();
        for (JsonNode product : body) {
            products.add(new ProductSummary(product.path("id").asLong(), localizedName(product.path("name"), "Produto sem nome")));
        }

        long totalCount = totalCount(response.getHeaders());
        boolean hasNext = hasNextPage(response.getHeaders(), safePage, safePerPage, products.size(), totalCount);

        LOGGER.info(
                "nuvemshop.api.list_products.done store_id={} page={} products_count={} total_count={} has_next={}",
                store.getStoreId(),
                safePage,
                products.size(),
                totalCount,
                hasNext
        );
        return new ProductPage(products, safePage, safePerPage, totalCount, hasNext, safeQuery);
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
        return sweepAllPages("nuvemshop.api.list_webhooks", store, "/v1/{storeId}/webhooks");
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
        return sweepAllPages("nuvemshop.api.list_scripts", store, "/v1/{storeId}/scripts");
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
            // Dashboard mostra apenas a primeira pagina de pedidos recentes; nao precisa varrer o historico.
            JsonNode response = restClient.get()
                    .uri(properties.apiBaseUrl() + "/v1/{storeId}/orders?page=1&per_page={perPage}", store.getStoreId(), DEFAULT_PER_PAGE)
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

    /**
     * Varre todas as paginas de uma colecao pequena (scripts/webhooks) e concatena num unico array.
     * Sem isso a chamada silenciosamente retornava so a primeira pagina (per_page default = 30).
     */
    private ArrayNode sweepAllPages(String event, Store store, String path) {
        LOGGER.info("{}.start store_id={}", event, store.getStoreId());
        ArrayNode all = JsonNodeFactory.instance.arrayNode();
        int page = 1;
        while (page <= MAX_PAGES_SWEEP) {
            URI uri = UriComponentsBuilder.fromUriString(properties.apiBaseUrl())
                    .path(path)
                    .queryParam("page", page)
                    .queryParam("per_page", MAX_PER_PAGE)
                    .encode()
                    .buildAndExpand(store.getStoreId())
                    .toUri();

            ResponseEntity<JsonNode> response;
            try {
                response = restClient.get()
                        .uri(uri)
                        .header("Authentication", "bearer " + store.getAccessToken())
                        .retrieve()
                        .toEntity(JsonNode.class);
            } catch (RuntimeException ex) {
                logRestFailure(event + ".error", store.getStoreId(), ex);
                throw ex;
            }

            JsonNode items = arrayBody(response.getBody());
            if (items == null) {
                LOGGER.warn(
                        "{}.unexpected_response store_id={} page={} response_present={}",
                        event,
                        store.getStoreId(),
                        page,
                        response.getBody() != null
                );
                break;
            }
            items.forEach(all::add);
            if (!hasNextPage(response.getHeaders(), page, MAX_PER_PAGE, items.size(), totalCount(response.getHeaders()))) {
                break;
            }
            page++;
        }
        if (page > MAX_PAGES_SWEEP) {
            LOGGER.warn("{}.page_limit_reached store_id={} max_pages={}", event, store.getStoreId(), MAX_PAGES_SWEEP);
        }
        LOGGER.info("{}.done store_id={} items_count={} pages_read={}", event, store.getStoreId(), all.size(), Math.min(page, MAX_PAGES_SWEEP));
        return all;
    }

    /**
     * A API responde array puro na maioria dos recursos, mas alguns vem envelopados em
     * `{"result": [...]}`. Desembrulhar aqui e obrigatorio: devolver vazio faria o
     * chamador concluir que nada esta instalado e recriar scripts/webhooks duplicados.
     */
    private JsonNode arrayBody(JsonNode body) {
        if (body == null) {
            return null;
        }
        if (body.isArray()) {
            return body;
        }
        JsonNode result = body.path("result");
        return result.isArray() ? result : null;
    }

    private long totalCount(HttpHeaders headers) {
        String raw = headers.getFirst("X-Total-Count");
        if (raw == null || raw.isBlank()) {
            return -1L;
        }
        try {
            return Long.parseLong(raw.strip());
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    /**
     * Prioriza o header Link (rel="next"), que e a fonte oficial da Nuvemshop.
     * Sem ele, cai para X-Total-Count e, por ultimo, para a heuristica "pagina cheia".
     */
    private boolean hasNextPage(HttpHeaders headers, int page, int perPage, int itemsInPage, long totalCount) {
        String link = headers.getFirst(HttpHeaders.LINK);
        if (link != null && !link.isBlank()) {
            return link.contains("rel=\"next\"") || link.contains("rel=next");
        }
        if (totalCount >= 0) {
            return (long) page * perPage < totalCount;
        }
        return itemsInPage == perPage;
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
