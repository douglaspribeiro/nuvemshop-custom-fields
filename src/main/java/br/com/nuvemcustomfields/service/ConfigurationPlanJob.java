package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;

@Component
public class ConfigurationPlanJob {
    private final PersonalizationRuleRepository rules;
    private final ConfigurationPlanService service;
    public ConfigurationPlanJob(PersonalizationRuleRepository rules, ConfigurationPlanService service) {
        this.rules = rules; this.service = service;
    }
    @Scheduled(fixedDelayString = "${personalization.plan-reconcile-delay-ms:60000}",
            initialDelayString = "${personalization.plan-reconcile-delay-ms:60000}")
    public void reconcile() {
        for (var storeId : rules.findConfiguredStoreIds()) {
            try { service.reconcile(storeId); }
            catch (RuntimeException ex) { LoggerFactory.getLogger(getClass()).warn(
                    "personalization.plan_reconcile.failed store_id={} type={}", storeId, ex.getClass().getSimpleName()); }
        }
    }
}
