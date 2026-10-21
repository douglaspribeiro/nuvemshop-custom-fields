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
import java.util.List;

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
                    // Texto vive em admin.dashboard.orders.unavailable: precisa seguir o idioma da loja.
                    null,
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
        var personalization = new OrderPersonalization(fieldRepository.findWithRuleByStoreId(store.getStoreId()));
        for (JsonNode order : orders) {
            List<String> properties = personalization.orderProperties(order);
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
