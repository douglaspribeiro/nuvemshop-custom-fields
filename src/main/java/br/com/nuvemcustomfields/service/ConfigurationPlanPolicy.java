package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** NC-04: whole configurations, stable oldest-first ordering, no field truncation. */
@Service
public class ConfigurationPlanPolicy {
    private final PlanCatalogService catalog;
    public ConfigurationPlanPolicy(PlanCatalogService catalog) { this.catalog = catalog; }
    public record Decision(Set<Long> allowedProducts, Map<Long, String> evidence) { }

    public Decision evaluate(Store store, List<PersonalizationRule> rules) {
        var free = catalog.activePlan(PlanType.FREE);
        var plan = store.getEffectivePlan().isBillable() ? catalog.activePlan(store.getEffectivePlan()) : free;
        long productLimit = store.getEffectivePlan().isBillable() ? plan.getProductLimit() : 1;
        Set<Long> allowed = new LinkedHashSet<>();
        Map<Long,String> evidence = new HashMap<>();
        // Paid configurations already available keep the existing paid-plan rendering rules.
        // NC-04 restores its own pauses only into capacity left by those configurations.
        if (store.getEffectivePlan().isBillable()) rules.stream()
                .filter(r -> r.isEnabled() && !r.isPlanPaused() && !r.getFields().isEmpty())
                .forEach(r -> allowed.add(r.getProductId()));
        long images = rules.stream().filter(r -> allowed.contains(r.getProductId()))
                .filter(r -> r.getFields().stream().anyMatch(f -> f.getFieldType() == FieldType.IMAGE_SELECT)).count();
        for (var rule : rules.stream().sorted(Comparator.comparing(PersonalizationRule::getCreatedAt)
                .thenComparing(PersonalizationRule::getId)).toList()) {
            // Snapshot includes the catalog version/limits and the exact field identities/types/options.
            String fields = rule.getFields().stream().sorted(Comparator.comparing(PersonalizationField::getId))
                    .map(f -> f.getId() + ":" + f.getFieldType() + ":" + f.imageOptions().size())
                    .collect(java.util.stream.Collectors.joining(","));
            evidence.put(rule.getProductId(), "NC04/v1;freeAsset=" + free.getId()
                    + ";freeFields=" + free.getFieldLimit() + ";freeImageProducts=" + free.getImageProductLimit()
                    + ";freeImageOptions=" + free.getImageOptionLimit() + ";freeEligible=" + fits(rule, free)
                    + ";effectivePlan=" + store.getEffectivePlan() + ";planAsset=" + plan.getId()
                    + ";productLimit=" + productLimit + ";fieldLimit=" + plan.getFieldLimit()
                    + ";imageProducts=" + plan.getImageProductLimit() + ";imageOptions=" + plan.getImageOptionLimit()
                    + ";createdAt=" + rule.getCreatedAt() + ";ruleId=" + rule.getId() + ";fields=" + fields);
            if (allowed.contains(rule.getProductId()) || !rule.isEnabled() || !fits(rule, plan)) continue;
            if (productLimit >= 0 && allowed.size() >= productLimit) continue;
            boolean hasImage = rule.getFields().stream().anyMatch(f -> f.getFieldType() == FieldType.IMAGE_SELECT);
            if (hasImage && plan.getImageProductLimit() >= 0 && images >= plan.getImageProductLimit()) continue;
            allowed.add(rule.getProductId());
            if (hasImage) images++;
        }
        return new Decision(Set.copyOf(allowed), Map.copyOf(evidence));
    }

    private boolean fits(PersonalizationRule rule, PlanAsset plan) {
        if (rule.getFields().isEmpty()) return false;
        if (plan.getFieldLimit() >= 0 && rule.getFields().size() > plan.getFieldLimit()) return false;
        return rule.getFields().stream().filter(f -> f.getFieldType() == FieldType.IMAGE_SELECT)
                .allMatch(f -> plan.getImageProductLimit() != 0 &&
                        (plan.getImageOptionLimit() < 0 || f.imageOptions().size() <= plan.getImageOptionLimit()));
    }
}
