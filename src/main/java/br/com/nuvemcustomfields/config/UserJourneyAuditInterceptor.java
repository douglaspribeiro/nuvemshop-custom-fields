package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.entity.UserJourneyAudit;
import br.com.nuvemcustomfields.service.UserJourneyAuditService;
import jakarta.servlet.http.*;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.support.RequestContextUtils;
import java.time.Instant;
import java.util.*;

@Component
public class UserJourneyAuditInterceptor implements HandlerInterceptor {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(UserJourneyAuditInterceptor.class);
    private static final String EVENT = UserJourneyAuditInterceptor.class.getName();
    private static final String JOURNEY = "auditJourneyId";
    private final UserJourneyAuditService audits;
    public UserJourneyAuditInterceptor(UserJourneyAuditService audits) { this.audits = audits; }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) return true;
        String route = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        // Image fetches and payment polling are excluded from journey persistence, but their errors are logged.
        var event = new UserJourneyAudit();
        event.action = method.getBeanType().getSimpleName() + "." + method.getMethod().getName();
        event.route = route; event.method = request.getMethod(); event.occurredAt = Instant.now();
        event.productId = numericVariable(request, "productId");
        if (event.productId == null && route.equals("/admin/onboarding/apply")) event.productId = number(request.getParameter("productId"));
        event.fieldId = numericVariable(request, "fieldId");
        try { event.selectedPlan = br.com.nuvemcustomfields.entity.PlanType.valueOf(request.getParameter("plan")).name(); }
        catch (IllegalArgumentException | NullPointerException ignored) { }

        request.setAttribute(EVENT, event);
        if (!route.equals("/oauth/callback") && !route.equals("/admin/nexo/session")) identify(request, event);
        if (event.storeId != null && !automated(event)) safe(() -> event.id = audits.start(event, true));
        return true;
    }
    @Override public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView mv) {
        if (mv != null && (mv.getModel().containsKey("error") || mv.getModel().containsKey("templateError") || mv.getModel().values().stream()
                .anyMatch(v -> v instanceof BindingResult b && b.hasErrors()))) request.setAttribute(EVENT + ".rejected", true);
        if (RequestContextUtils.getOutputFlashMap(request).containsKey("error")) request.setAttribute(EVENT + ".rejected", true);
    }
    @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!(request.getAttribute(EVENT) instanceof UserJourneyAudit event)) return;
        if (event.storeId == null && ex == null && response.getStatus() < 400) identify(request, event); // OAuth/Nexo establish the store during the handler.
        String result = ex != null || response.getStatus() >= 500 ? "ERROR"
                : response.getStatus() >= 400 || Boolean.TRUE.equals(request.getAttribute(EVENT + ".rejected")) ? "REJECTED" : "COMPLETED";
        if (response.getStatus() >= 300 && response.getStatus() < 400
                && "/admin/embedded".equals(response.getHeader("Location"))) result = "ACCESS_REQUIRED";
        final String outcome = result;
        if (outcome.equals("ERROR") || outcome.equals("REJECTED")) {
            LOGGER.warn("admin.error action={} store_id={} product_id={} field_id={} outcome={} status={} error_type={}",
                    event.action,event.storeId,event.productId,event.fieldId,outcome,response.getStatus(),
                    ex == null ? "none" : ex.getClass().getSimpleName());
        } else if (outcome.equals("COMPLETED") && Set.of("POST","PUT","PATCH","DELETE").contains(event.method)) {
            LOGGER.info("admin.change action={} store_id={} product_id={} field_id={} actor={} status={}",
                    event.action,event.storeId,event.productId,event.fieldId,event.actor,response.getStatus());
        }
        if (event.storeId == null || automated(event)) return;
        safe(() -> {
            if (event.id == null) event.id = audits.start(event, false);
            audits.complete(event.id, outcome, response.getStatus());
        });
    }
    private static boolean automated(UserJourneyAudit event) {
        return event.route.equals("/admin/images/{id}") || event.route.equals("/admin/billing/status");
    }
    private void identify(HttpServletRequest request, UserJourneyAudit event) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY) instanceof Long id)) return;
        event.storeId = id;
        event.actor = Boolean.TRUE.equals(session.getAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY)) ? "OPERATOR" : "MERCHANT";
        synchronized (session) {
            if (!(session.getAttribute(JOURNEY) instanceof String)) session.setAttribute(JOURNEY, UUID.randomUUID().toString());
            event.journeyId = (String) session.getAttribute(JOURNEY);
        }
    }
    private static Long numericVariable(HttpServletRequest request, String key) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return vars instanceof Map<?, ?> map ? number(map.get(key)) : null;
    }
    private static Long number(Object value) {
        try { return value == null ? null : Long.valueOf(value.toString()); } catch (NumberFormatException ex) { return null; }
    }
    private static void safe(Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException ex) {
            // Never log request data or exception messages (which may contain payloads).
            LOGGER.warn("audit.journey.persistence_failed type={}", ex.getClass().getSimpleName());
        }
    }
}
