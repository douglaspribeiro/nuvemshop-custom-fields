package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.properties.FeatureAccessProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ProductComplementsAccessInterceptor implements HandlerInterceptor {
    private final FeatureAccessProperties features;

    public ProductComplementsAccessInterceptor(FeatureAccessProperties features) {
        this.features = features;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        var session = request.getSession(false);
        if (session != null && session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY) instanceof Long storeId
                && features.allows(storeId, FeatureAccessProperties.Feature.PRODUCT_COMPLEMENTS)) return true;
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return false;
    }
}
