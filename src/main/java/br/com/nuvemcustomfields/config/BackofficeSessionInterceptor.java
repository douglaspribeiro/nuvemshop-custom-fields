package br.com.nuvemcustomfields.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class BackofficeSessionInterceptor implements HandlerInterceptor {

    public static final String SESSION_KEY = "backofficeAuthenticated";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Object authenticated = request.getSession().getAttribute(SESSION_KEY);
        if (Boolean.TRUE.equals(authenticated)) {
            return true;
        }
        response.sendRedirect("/backoffice/login");
        return false;
    }
}
