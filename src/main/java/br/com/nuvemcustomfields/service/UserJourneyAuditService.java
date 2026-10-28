package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.UserJourneyAudit;
import br.com.nuvemcustomfields.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;

@Service
public class UserJourneyAuditService {
    private final UserJourneyAuditRepository audits;
    private final PersonalizationRuleRepository rules;
    private final PersonalizationFieldRepository fields;
    private final StoreRepository stores;
    public UserJourneyAuditService(UserJourneyAuditRepository audits, PersonalizationRuleRepository rules,
            PersonalizationFieldRepository fields, StoreRepository stores) {
        this.audits = audits; this.rules = rules; this.fields = fields; this.stores = stores;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(UserJourneyAudit event, boolean captureBefore) {
        if (stores.findByStoreId(event.storeId).isEmpty()) return null;
        event.beforeState = captureBefore ? state(event.storeId, event.productId) : null;
        return audits.saveAndFlush(event).id;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long id, String outcome, int httpStatus) {
        if (id == null) return;
        var event = audits.findById(id).orElse(null);
        if (event == null) return;
        event.completedAt = Instant.now(); event.outcome = outcome; event.httpStatus = httpStatus;
        event.afterState = state(event.storeId, event.productId);
    }
    private String state(Long storeId, Long productId) {
        // Structural metadata only: no labels, options, tokens, personal data or payment payloads.
        if (productId == null) return "{\"products\":" + rules.countByStoreId(storeId)
                + ",\"fields\":" + fields.countByStoreId(storeId) + "}";
        return rules.findByStoreIdAndProductId(storeId, productId).map(rule -> {
            var metadata = fields.findByRuleIdOrderBySortOrderAscIdAsc(rule.getId()).stream()
                    .map(f -> "{\"id\":" + f.getId() + ",\"type\":\"" + f.getFieldType()
                            + "\",\"required\":" + f.isRequired() + ",\"order\":" + f.getSortOrder() + "}")
                    .collect(java.util.stream.Collectors.joining(","));
            return "{\"exists\":true,\"enabled\":" + rule.isEnabled() + ",\"fields\":[" + metadata + "]}";
        }).orElse("{\"exists\":false}");
    }
}
