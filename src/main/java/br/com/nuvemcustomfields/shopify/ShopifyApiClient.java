package br.com.nuvemcustomfields.shopify;

import br.com.nuvemcustomfields.dto.ProductSummary;
import br.com.nuvemcustomfields.entity.ShopifyShop;
import br.com.nuvemcustomfields.properties.ShopifyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ShopifyApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShopifyApiClient.class);

    private final ShopifyProperties properties;
    private final RestClient restClient;

    public ShopifyApiClient(ShopifyProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    public JsonNode exchangeCode(String shopDomain, String code) {
        LOGGER.info("shopify.api.oauth.exchange.start shop={}", shopDomain);
        return restClient.post()
                .uri("https://{shop}/admin/oauth/access_token", shopDomain)
                .body(Map.of(
                        "client_id", properties.clientId(),
                        "client_secret", properties.clientSecret(),
                        "code", code
                ))
                .retrieve()
                .body(JsonNode.class);
    }

    public List<ProductSummary> listProducts(ShopifyShop shop) {
        LOGGER.info("shopify.api.products.start shop_id={} shop={}", shop.getId(), shop.getShopDomain());
        JsonNode response = graphQl(shop, """
                query Products {
                  products(first: 50, sortKey: TITLE) {
                    nodes {
                      legacyResourceId
                      title
                    }
                  }
                }
                """);
        List<ProductSummary> products = new ArrayList<>();
        JsonNode nodes = response == null ? null : response.path("data").path("products").path("nodes");
        if (nodes == null || !nodes.isArray()) {
            LOGGER.warn("shopify.api.products.unexpected_response shop_id={} response_present={}", shop.getId(), response != null);
            return products;
        }
        for (JsonNode node : nodes) {
            products.add(new ProductSummary(node.path("legacyResourceId").asLong(), node.path("title").asText("Produto sem nome")));
        }
        LOGGER.info("shopify.api.products.done shop_id={} products_count={}", shop.getId(), products.size());
        return products;
    }

    public JsonNode shopInfo(ShopifyShop shop) {
        return graphQl(shop, """
                query ShopInfo {
                  shop {
                    id
                    name
                    myshopifyDomain
                  }
                }
                """);
    }

    private JsonNode graphQl(ShopifyShop shop, String query) {
        return restClient.post()
                .uri("https://{shop}/admin/api/{version}/graphql.json", shop.getShopDomain(), properties.apiVersion())
                .header("X-Shopify-Access-Token", shop.getAccessToken())
                .body(Map.of("query", query))
                .retrieve()
                .body(JsonNode.class);
    }
}
