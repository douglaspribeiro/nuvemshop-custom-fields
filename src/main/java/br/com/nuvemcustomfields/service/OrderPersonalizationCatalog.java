package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PersonalizationField;
import br.com.nuvemcustomfields.entity.PersonalizationFieldHistory;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPersonalizationCatalog {
    static final class Unavailable extends RuntimeException {
        Unavailable(Throwable cause) { super("Não foi possível ler o histórico de campos da loja.",cause); }
    }
    private final PersonalizationFieldRepository fields;
    private final PersonalizationFieldHistoryRepository history;
    private final StoreConfigurationSnapshotRepository snapshots;
    private final ObjectMapper json;
    public OrderPersonalizationCatalog(PersonalizationFieldRepository fields, PersonalizationFieldHistoryRepository history,
            StoreConfigurationSnapshotRepository snapshots, ObjectMapper json) {
        this.fields=fields; this.history=history; this.snapshots=snapshots; this.json=json;
    }
    @Transactional(readOnly=true)
    OrderPersonalization forStore(Long storeId) {
        var matcher=new OrderPersonalization(fields.findWithRuleByStoreId(storeId));
        for (var field:history.findByStoreId(storeId)) matcher.addField(field.getProductId(),field.getPropertyName());
        for (var snapshot:snapshots.findByStoreIdOrderByUninstalledAtDesc(storeId)) {
            try {
                var data=json.readTree(snapshot.getConfigurationJson());
                if (data == null || !data.path("products").isArray()) throw new Unavailable(null);
                for (var product:data.path("products")) {
                    for (var field:product.path("fields")) matcher.addField(product.path("productId").asLong(),field.path("label").asText());
                }
            } catch (JsonProcessingException ex) {
                // Never classify a damaged historical catalog as a confirmed empty result.
                throw new Unavailable(ex);
            }
        }
        return matcher;
    }
    // Called under the existing store lock and transaction used by configuration writes.
    public void remember(PersonalizationField field) {
        var rule=field.getRule();
        String name=OrderPersonalization.propertyName(field.getLabel());
        if (!name.isBlank() && !history.existsByStoreIdAndProductIdAndPropertyName(rule.getStoreId(),rule.getProductId(),name))
            history.saveAndFlush(new PersonalizationFieldHistory(rule.getStoreId(),rule.getProductId(),name));
    }
}
