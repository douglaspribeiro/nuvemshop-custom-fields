package br.com.nuvemcustomfields.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Set;

public class MerchantUiLayoutInterceptor implements HandlerInterceptor {
    private static final Set<String> CLASSIC_PAGES = Set.of(
            "admin/index", "admin/products", "admin/fields",
            "admin/apps", "admin/complements", "admin/complement-form"
    );

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        if (modelAndView == null || Boolean.TRUE.equals(modelAndView.getModel().get("newMerchantUi"))) {
            return;
        }
        String view = modelAndView.getViewName();
        if (view != null && CLASSIC_PAGES.contains(view)) {
            modelAndView.setViewName("admin/classic/" + view.substring("admin/".length()));
        }
    }
}
