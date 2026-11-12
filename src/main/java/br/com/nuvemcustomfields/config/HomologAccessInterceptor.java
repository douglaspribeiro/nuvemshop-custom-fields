package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.properties.HomologProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import java.util.Map;

@Component
public class HomologAccessInterceptor implements HandlerInterceptor {
    private final HomologProperties homolog;
    public HomologAccessInterceptor(HomologProperties homolog) { this.homolog = homolog; }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        var session = request.getSession(false);
        Object id = session == null ? null : session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
        boolean allowed = homolog.allows(id instanceof Long store ? store : null);
        request.setAttribute("homologFeaturesAllowed", allowed);
        boolean restricted = path.equals("/admin/templates") || path.startsWith("/admin/templates/")
                || path.equals("/admin/buyer-uploads") || path.startsWith("/admin/buyer-uploads/")
                || path.matches("/admin/products/[0-9]+/save-template");
        if (path.matches("/public/stores/[0-9]+/buyer-uploads(?:/.*)?")) {
            Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            String store = variables instanceof Map<?, ?> values ? String.valueOf(values.get("store")) : "";
            try { allowed = homolog.allows(Long.valueOf(store)); } catch (NumberFormatException ex) { allowed = false; }
            restricted = true;
        }
        if (restricted && !allowed) { response.sendError(404); return false; }
        return true;
    }
    @Override public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView view) {
        if (view == null || view.getView() instanceof org.springframework.web.servlet.view.RedirectView
                || (view.getViewName() != null && view.getViewName().startsWith("redirect:"))) return;
        boolean allowed = Boolean.TRUE.equals(request.getAttribute("homologFeaturesAllowed"));
        view.addObject("homologFeaturesAllowed", allowed);
        if (!allowed && view.getModel().get("fieldTypes") instanceof br.com.nuvemcustomfields.entity.FieldType[] types)
            view.addObject("fieldTypes", java.util.Arrays.stream(types)
                    .filter(type -> type != br.com.nuvemcustomfields.entity.FieldType.FILE_UPLOAD).toArray(br.com.nuvemcustomfields.entity.FieldType[]::new));
    }
}
