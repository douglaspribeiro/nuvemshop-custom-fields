package br.com.nuvemcustomfields.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class BackofficeSessionInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackofficeSessionInterceptor.class);

    public static final String SESSION_KEY = "backofficeAuthenticated";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Object authenticated = request.getSession().getAttribute(SESSION_KEY);
        if (Boolean.TRUE.equals(authenticated)) {
            LOGGER.info("backoffice.session.check uri={} authenticated=true", request.getRequestURI());
            return true;
        }
        LOGGER.warn(
                "backoffice.session.redirect_login uri={} session_id={} authenticated={}",
                request.getRequestURI(),
                request.getSession().getId(),
                authenticated
        );
        response.sendRedirect("/backoffice/login");
        return false;
    }
}
