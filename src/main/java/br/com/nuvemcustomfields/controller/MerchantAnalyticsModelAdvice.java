package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.HandlerMapping;

@ControllerAdvice
public class MerchantAnalyticsModelAdvice {
    @ModelAttribute("ga4OperatorSession")
    public boolean operator(HttpServletRequest request) {
        var session=request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY));
    }

    @ModelAttribute("ga4StoreContext")
    public Map<String,String> context(HttpServletRequest request) {
        var session=request.getSession(false);
        String path=request.getRequestURI().substring(request.getContextPath().length());
        if (session == null || operator(request) || !(path.equals("/admin") || path.startsWith("/admin/"))
                || path.equals("/admin/embedded") || path.equals("/admin/nexo/session")
                || !(session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY) instanceof Long id) || id <= 0) return Map.of();
        Object matched=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route=matched instanceof String value ? value : path;
        return Map.of("store_id",id.toString(),"admin_screen",screen(route));
    }

    private String screen(String route) {
        if (route.contains("/billing/reactivate")) return "reactivation";
        if (route.contains("/billing/upgrade")) return route.endsWith("/pay") ? "upgrade_payment" : "upgrade";
        if (route.contains("/billing/pay")) return "subscription_payment";
        if (route.contains("/billing/processing")) return "payment_processing";
        if (route.contains("/billing/cancel")) return "subscription_cancellation";
        if (route.contains("/billing")) return "plans";
        if (route.contains("/fields")) return "product_fields";
        if (route.contains("/onboarding")) return "setup";
        if (route.contains("/settings")) return "appearance";
        if (route.contains("/suggestions")) return "suggestions";
        if (route.contains("/dashboard")) return "sales";
        if (route.contains("/products")) return "products";
        if (route.contains("/help")) return "help";
        return "overview";
    }
}
