package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminSessionInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminSessionInterceptor.class);

    public static final String STORE_SESSION_KEY = "storeId";

    private final StoreRepository storeRepository;

    public AdminSessionInterceptor(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Object storeId = request.getSession().getAttribute(STORE_SESSION_KEY);
        if (storeId instanceof Long id) {
            boolean activeStoreFound = storeRepository.findActiveByStoreId(id).isPresent();
            LOGGER.info(
                    "admin.session.check uri={} store_id={} active_store_found={}",
                    request.getRequestURI(),
                    id,
                    activeStoreFound
            );
            if (activeStoreFound) {
                return true;
            }
        } else {
            LOGGER.warn(
                    "admin.session.missing uri={} session_id={} store_id={}",
                    request.getRequestURI(),
                    request.getSession().getId(),
                    storeId
            );
        }
        LOGGER.warn("admin.session.redirect_install uri={}", request.getRequestURI());
        response.sendRedirect("/install");
        return false;
    }
}
