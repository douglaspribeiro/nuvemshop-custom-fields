package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.DashboardSummary;
import br.com.nuvemcustomfields.dto.PersonalizedOrderSummary;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private final PersonalizationRuleRepository ruleRepository;
    private final PersonalizationFieldRepository fieldRepository;
    private final NuvemshopApiClient apiClient;

    public ReportService(
            PersonalizationRuleRepository ruleRepository,
            PersonalizationFieldRepository fieldRepository,
            NuvemshopApiClient apiClient
    ) {
        this.ruleRepository = ruleRepository;
        this.fieldRepository = fieldRepository;
        this.apiClient = apiClient;
    }

    public DashboardSummary dashboard(Store store) {
        long fields = ruleRepository.findByStoreIdOrderByProductNameAsc(store.getStoreId()).stream()
                .mapToLong(rule -> fieldRepository.countByRuleId(rule.getId()))
                .sum();
        if (!hasScope(store, "read_orders")) {
            return new DashboardSummary(
                    ruleRepository.countByStoreId(store.getStoreId()),
                    fields,
                    false,
                    "A loja foi instalada sem o escopo read_orders. Reinstale o app para liberar a leitura de pedidos.",
                    List.of()
            );
        }
        return new DashboardSummary(
                ruleRepository.countByStoreId(store.getStoreId()),
                fields,
                true,
                null,
                personalizedOrders(store)
        );
    }

    private List<PersonalizedOrderSummary> personalizedOrders(Store store) {
        JsonNode orders;
        try {
            orders = apiClient.listRecentOrders(store);
        } catch (HttpClientErrorException.Forbidden ex) {
            return List.of();
        } catch (HttpClientErrorException.NotFound ex) {
            return List.of();
        }
        List<PersonalizedOrderSummary> result = new ArrayList<>();
        if (orders == null || !orders.isArray()) {
            return result;
        }
        for (JsonNode order : orders) {
            List<String> properties = collectProperties(order);
            if (!properties.isEmpty()) {
                result.add(new PersonalizedOrderSummary(
                        order.path("id").asLong(),
                        order.path("number").asText(order.path("id").asText()),
                        order.path("created_at").asText(""),
                        properties
                ));
            }
        }
        return result;
    }

    private List<String> collectProperties(JsonNode order) {
        List<String> properties = new ArrayList<>();
        JsonNode products = order.path("products");
        if (!products.isArray()) {
            return properties;
        }
        for (JsonNode product : products) {
            JsonNode node = product.path("properties");
            if (node.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    properties.add(entry.getKey() + ": " + entry.getValue().asText());
                }
            } else if (node.isArray()) {
                for (JsonNode property : node) {
                    String name = property.path("name").asText(property.path("key").asText(""));
                    String value = property.path("value").asText("");
                    if (!name.isBlank() || !value.isBlank()) {
                        properties.add(name + ": " + value);
                    }
                }
            }
        }
        return properties;
    }

    private boolean hasScope(Store store, String requiredScope) {
        if (store.getScope() == null || store.getScope().isBlank()) {
            return false;
        }
        for (String scope : store.getScope().split(",")) {
            if (requiredScope.equals(scope.strip())) {
                return true;
            }
        }
        return false;
    }
}
