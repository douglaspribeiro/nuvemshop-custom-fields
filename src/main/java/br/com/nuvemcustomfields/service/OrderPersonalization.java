package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PersonalizationField;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Matches order item properties against the app's currently configured product fields. */
final class OrderPersonalization {
    private final Map<Long, Set<String>> fieldNames = new HashMap<>();

    OrderPersonalization(List<PersonalizationField> fields) {
        for (var field : fields) {
            addField(field.getRule().getProductId(),field.getLabel());
        }
    }

    static String propertyName(String label) { return label.replace('[', '(').replace(']', ')').strip(); }

    void addField(Long productId, String label) {
        if (productId != null && productId > 0 && label != null && !label.isBlank())
            fieldNames.computeIfAbsent(productId, ignored -> new HashSet<>()).add(propertyName(label));
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
                String value = displayValue(entry.getValue());
                if (names.contains(entry.getKey()) && !value.isBlank()) {
                    properties.add(entry.getKey() + ": " + value);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode property : node) {
                String name = property.path("name").asText(property.path("key").asText(""));
                String value = displayValue(property.path("value"));
                if (names.contains(name) && !value.isBlank()) {
                    properties.add(name + ": " + value);
                }
            }
        }
        return properties;
    }

    private static String displayValue(JsonNode value) {
        if (value.isArray()) {
            List<String> selected = new ArrayList<>();
            for (JsonNode item : value) {
                if (item.isValueNode() && !item.isNull() && !item.asText("").isBlank()) selected.add(item.asText());
            }
            return String.join(", ", selected);
        }
        return value.isValueNode() && !value.isNull() ? value.asText("") : "";
    }

}
