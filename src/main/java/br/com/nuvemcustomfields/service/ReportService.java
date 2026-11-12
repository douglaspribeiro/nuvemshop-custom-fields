package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.DashboardSummary;
import br.com.nuvemcustomfields.dto.PersonalizedOrderSummary;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReportService {
    @org.springframework.beans.factory.annotation.Autowired
    private OrderPersonalizationCatalog personalizationCatalog;

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
                    // Texto vive em admin.dashboard.orders.unavailable: precisa seguir o idioma da loja.
                    null,
                    List.of()
            );
        }
        try {
            JsonNode orders = apiClient.listRecentOrders(store);
            if (orders == null || !orders.isArray()) {
                return unavailable(store, fields);
            }
            return new DashboardSummary(ruleRepository.countByStoreId(store.getStoreId()), fields,
                    true, null, personalizedOrders(store, orders));
        } catch (RestClientException | OrderPersonalizationCatalog.Unavailable ex) {
            return unavailable(store, fields);
        }
    }

    private DashboardSummary unavailable(Store store, long fields) {
        return new DashboardSummary(ruleRepository.countByStoreId(store.getStoreId()), fields,
                false, "load_failed", List.of());
    }

    private List<PersonalizedOrderSummary> personalizedOrders(Store store, JsonNode orders) {
        List<PersonalizedOrderSummary> result = new ArrayList<>();
        var personalization = personalizationCatalog == null ? new OrderPersonalization(fieldRepository.findWithRuleByStoreId(store.getStoreId()))
                    : personalizationCatalog.forStore(store.getStoreId());
        for (JsonNode order : orders) {
            List<PersonalizedOrderSummary.Item> items = new ArrayList<>();
            JsonNode products = order.path("products");
            if (!products.isArray()) {
                continue;
            }
            for (int index = 0; index < products.size(); index++) {
                JsonNode product = products.get(index);
                List<String> properties = personalization.productProperties(product);
                if (!properties.isEmpty()) {
                    items.add(new PersonalizedOrderSummary.Item(index + 1,
                            product.path("product_id").asText(),
                            product.path("name").asText("").strip(),
                            orderItemImageUrl(product),
                            product.path("quantity").asInt(), properties,
                            java.util.regex.Pattern.compile("NCF-FILE:([a-f0-9-]{36})")
                                    .matcher(product.path("properties").toString()).results()
                                    .map(match -> match.group(1)).distinct().toList()));
                }
            }
            if (!items.isEmpty()) {
                result.add(new PersonalizedOrderSummary(
                        order.path("id").asLong(),
                        order.path("number").asText(order.path("id").asText()),
                        order.path("created_at").asText(""),
                        items
                ));
            }
        }
        return result;
    }

    private static String orderItemImageUrl(JsonNode product) {
        String src = product.path("image").path("src").asText("").strip();
        if (src.startsWith("//")) {
            return "https:" + src;
        }
        if (src.startsWith("http://")) {
            return "https://" + src.substring("http://".length());
        }
        return src.startsWith("https://") ? src : null;
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
