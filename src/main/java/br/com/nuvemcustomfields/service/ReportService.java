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
    @org.springframework.beans.factory.annotation.Autowired
    private BuyerUploadOrders uploadOrders;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

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
            if(uploadOrders!=null) {
                try {uploadOrders.process(store.getStoreId(),order);}
                catch(RuntimeException error) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("buyer.order.reconcile.failed store_id={} order_id={} type={}",store.getStoreId(),order.path("id").asLong(),error.getClass().getSimpleName());}
            }
            List<PersonalizedOrderSummary.Item> items = new ArrayList<>();
            JsonNode products = order.path("products");
            if (!products.isArray()) {
                continue;
            }
            for (int index = 0; index < products.size(); index++) {
                JsonNode product = products.get(index);
                List<String> properties = personalization.productProperties(product);
                if (!properties.isEmpty()) {
                    var uploadIds=uploadOrders!=null ? uploadOrders.fileIds(store.getStoreId(),product) : java.util.regex.Pattern.compile("NCF-FILE:([a-f0-9-]{36})")
                            .matcher(product.path("properties").toString()).results().map(match->match.group(1)).distinct().toList();
                    items.add(new PersonalizedOrderSummary.Item(index + 1,
                            product.path("product_id").asText(),
                            product.path("name").asText("").strip(),
                            orderItemImageUrl(product),
                            product.path("quantity").asInt(), properties,
                            uploadIds, fieldGroups(store.getStoreId(),properties,uploadIds)));
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

    private List<PersonalizedOrderSummary.Property> fieldGroups(long storeId,List<String> properties,List<String> ids) {
        var result=new ArrayList<PersonalizedOrderSummary.Property>();
        for(var property:properties) {
            var parts=property.split(": ",2);
            result.add(new PersonalizedOrderSummary.Property(parts[0],parts.length>1?parts[1]:"",List.of()));
        }
        if(jdbc==null || ids.isEmpty())return result;
        var placeholders=String.join(",",java.util.Collections.nCopies(ids.size(),"?"));
        var args=new ArrayList<Object>();args.add(storeId);args.addAll(ids);
        var rows=jdbc.queryForList("select u.id,u.field_id,u.original_name,f.label from buyer_uploads u left join personalization_fields f on f.id=u.field_id where u.store_id=? and u.id in ("+placeholders+") and u.state='ORDERED' order by u.created_at,u.id",args.toArray());
        var grouped=new java.util.LinkedHashMap<String,List<PersonalizedOrderSummary.Upload>>();
        for(var row:rows) {
            String label=java.util.Objects.toString(row.get("label"),"Arquivos");
            grouped.computeIfAbsent(label,key->new ArrayList<>()).add(new PersonalizedOrderSummary.Upload((String)row.get("id"),java.util.Objects.toString(row.get("original_name"),"Arquivo")));
        }
        for(var group:grouped.entrySet()) {
            int index=-1;
            for(int n=0;n<result.size();n++)if(result.get(n).label().equals(group.getKey())){index=n;break;}
            var property=new PersonalizedOrderSummary.Property(group.getKey(),"",List.copyOf(group.getValue()));
            if(index>=0)result.set(index,property);else result.add(property);
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
