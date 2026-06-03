package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.ProductSummary;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class NuvemshopApiClient {

    private final NuvemshopProperties properties;
    private final RestClient restClient;

    public NuvemshopApiClient(NuvemshopProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.defaultHeader("User-Agent", properties.userAgent()).build();
    }

    public List<ProductSummary> listProducts(Store store) {
        JsonNode response = restClient.get()
                .uri(properties.apiBaseUrl() + "/v1/{storeId}/products", store.getStoreId())
                .header("Authentication", "bearer " + store.getAccessToken())
                .retrieve()
                .body(JsonNode.class);

        List<ProductSummary> products = new ArrayList<>();
        if (response == null || !response.isArray()) {
            return products;
        }
        for (JsonNode product : response) {
            products.add(new ProductSummary(product.path("id").asLong(), localizedName(product.path("name"))));
        }
        return products;
    }

    private String localizedName(JsonNode nameNode) {
        if (nameNode == null || nameNode.isMissingNode() || nameNode.isNull()) {
            return "Produto sem nome";
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
        return "Produto sem nome";
    }
}
