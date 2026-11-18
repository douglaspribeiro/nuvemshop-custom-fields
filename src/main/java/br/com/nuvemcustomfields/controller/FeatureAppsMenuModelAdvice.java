package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.properties.FeatureAccessProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class FeatureAppsMenuModelAdvice {
    private final FeatureAccessProperties features;

    public FeatureAppsMenuModelAdvice(FeatureAccessProperties features) {
        this.features = features;
    }

    @ModelAttribute("appsMenuVisible")
    public boolean appsMenuVisible(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY) instanceof Long storeId)) return false;
        return features.allows(storeId, FeatureAccessProperties.Feature.APPS_MENU);
    }

    @ModelAttribute("scheduledPixVisible")
    public boolean scheduledPixVisible(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null && session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY) instanceof Long id
                && features.allows(id, FeatureAccessProperties.Feature.SCHEDULED_PIX);
    }
}
