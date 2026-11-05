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
    private final PaymentSubscriptionRepository subscriptions;
    private final br.com.nuvemcustomfields.service.PaymentSubscriptionService payments;
    public ConfigurationPlanModelAdvice(StoreRepository stores, PersonalizationRuleRepository rules, ConfigurationPlanPolicy policy,
            PaymentSubscriptionRepository subscriptions, br.com.nuvemcustomfields.service.PaymentSubscriptionService payments) {
        this.stores=stores; this.rules=rules; this.policy=policy; this.subscriptions=subscriptions; this.payments=payments;
    }
    public record BillingAlert(String kind, java.time.Instant graceUntil, String actionPath) { }

    @ModelAttribute("billingAlert")
    @Transactional(readOnly=true)
    public BillingAlert billing(HttpServletRequest request) {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        if (!path.equals("/admin") && !path.equals("/admin/")) return null;
        var session=request.getSession(false);
        if (session==null || !(session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY) instanceof Long id)) return null;
        var store=stores.findActiveByStoreId(id).orElse(null);
        if (store==null) return null;
        var sub=subscriptions.findByStoreId(id).orElse(null);
        String kind=null;
        java.time.Instant grace=null;
        if (sub!=null && sub.getRenewalFailureDetectedAt()!=null) {
            boolean inGrace=sub.getGraceUntil()!=null && sub.getGraceUntil().isAfter(java.time.Instant.now())
                    && store.getEffectivePlan().isBillable();
            kind=inGrace ? "grace" : "overdue";
            if (inGrace) grace=sub.getGraceUntil();
        } else if (sub!=null && sub.getStatus()==br.com.nuvemcustomfields.entity.PaymentSubscriptionStatus.PAST_DUE) kind="overdue";
        else if (store.isBillingSuspended()) kind="suspended";
        else if (sub!=null && sub.getStatus()==br.com.nuvemcustomfields.entity.PaymentSubscriptionStatus.ERROR) kind="error";
        else if (!store.getEffectivePlan().isBillable() && store.getPremiumBonusExpiresAt()!=null
                && !store.isPremiumBonusActive()) kind="bonusEnded";
        if (kind==null) return null;
        String action=payments.reactivationPlan(id).isPresent() ? "/admin/billing/reactivate?storeId="+id : "/admin/billing";
        return new BillingAlert(kind,grace,action);
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
