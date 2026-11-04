package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.ConfigurationPlanPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.stream.Collectors;

@ControllerAdvice(assignableTypes = AdminController.class)
public class ConfigurationPlanModelAdvice {
    private final StoreRepository stores;
    private final PersonalizationRuleRepository rules;
    private final ConfigurationPlanPolicy policy;
    public ConfigurationPlanModelAdvice(StoreRepository stores, PersonalizationRuleRepository rules, ConfigurationPlanPolicy policy) {
        this.stores=stores; this.rules=rules; this.policy=policy;
    }
    @ModelAttribute("planPausedProductIds")
    @Transactional(readOnly=true)
    public Set<Long> paused(HttpServletRequest request) {
        var session=request.getSession(false);
        if (session==null || !(session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY) instanceof Long id)) return Set.of();
        var store=stores.findActiveByStoreId(id).orElse(null);
        if (store==null) return Set.of();
        var configured=rules.findWithFieldsByStoreIdOrderByCreatedAtAscIdAsc(id);
        var allowed=policy.evaluate(store, configured).allowedProducts();
        return configured.stream().filter(r -> r.isEnabled() && !r.getFields().isEmpty() && !allowed.contains(r.getProductId()))
                .map(r -> r.getProductId()).collect(Collectors.toSet());
    }
}
