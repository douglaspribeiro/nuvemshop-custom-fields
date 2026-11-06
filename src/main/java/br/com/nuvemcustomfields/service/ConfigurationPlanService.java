package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.ConfigurationPauseEmail;
import br.com.nuvemcustomfields.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;

@Service
public class ConfigurationPlanService {
    private final StoreRepository stores;
    private final PersonalizationRuleRepository rules;
    private final ConfigurationPlanPolicy policy;
    private final ConfigurationPauseEmailRepository emails;
    private final StorefrontCache cache;
    public ConfigurationPlanService(StoreRepository stores, PersonalizationRuleRepository rules,
            ConfigurationPlanPolicy policy, ConfigurationPauseEmailRepository emails, StorefrontCache cache) {
        this.stores = stores; this.rules = rules; this.policy = policy; this.emails = emails; this.cache = cache;
    }

    @Transactional
    public void reconcile(Long storeId) {
        var store = stores.findActiveByStoreIdForUpdate(storeId).orElse(null);
        if (store == null) return;
        var configured = rules.findWithFieldsByStoreIdOrderByCreatedAtAscIdAsc(storeId);
        var decision = policy.evaluate(store, configured);
        var newlyPaused = new ArrayList<Long>();
        boolean changed = false;
        for (var rule : configured) {
            rule.recordPlanEligibility(decision.evidence().get(rule.getProductId()));
            // A manually disabled rule is never enabled or newly paused by NC-04.
            if (!rule.isEnabled()) continue;
            if (store.getEffectivePlan().isBillable() && !rule.isPlanPaused()) continue;
            boolean pause = !decision.allowedProducts().contains(rule.getProductId());
            if (pause != rule.isPlanPaused()) {
                if (pause && !rule.getFields().isEmpty()) newlyPaused.add(rule.getProductId());
                rule.setPlanPaused(pause);
                changed = true;
            }
        }
        rules.saveAll(configured);
        if (changed) cache.invalidateStoreAfterCommit(storeId);
        if (!newlyPaused.isEmpty()) emails.save(new ConfigurationPauseEmail(storeId,
                newlyPaused.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))));
    }
}
