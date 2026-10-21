package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PersonalizationField;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Matches order item properties against the app's currently configured product fields. */
final class OrderPersonalization {
    private final Map<Long, Set<String>> fieldNames = new HashMap<>();

    OrderPersonalization(List<PersonalizationField> fields) {
        for (var field : fields) {
            fieldNames.computeIfAbsent(field.getRule().getProductId(), ignored -> new HashSet<>())
                    .add(field.getLabel().replace('[', '(').replace(']', ')').strip());
        }
    }

    List<String> orderProperties(JsonNode order) {
        List<String> properties = new ArrayList<>();
        JsonNode products = order.path("products");
        if (!products.isArray()) {
            return properties;
        }
        for (JsonNode product : products) {
            properties.addAll(productProperties(product));
        }
        return properties;
    }

    List<String> productProperties(JsonNode product) {
        List<String> properties = new ArrayList<>();
        Set<String> names = fieldNames.getOrDefault(product.path("product_id").asLong(), Set.of());
        if (names.isEmpty()) return properties;
        JsonNode node = product.path("properties");
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (names.contains(entry.getKey()) && !entry.getValue().asText().isBlank()) {
                    properties.add(entry.getKey() + ": " + entry.getValue().asText());
                }
            }
        } else if (node.isArray()) {
            for (JsonNode property : node) {
                String name = property.path("name").asText(property.path("key").asText(""));
                String value = property.path("value").asText("");
                if (names.contains(name) && !value.isBlank()) {
                    properties.add(name + ": " + value);
                }
            }
        }
        return properties;
    }

}
