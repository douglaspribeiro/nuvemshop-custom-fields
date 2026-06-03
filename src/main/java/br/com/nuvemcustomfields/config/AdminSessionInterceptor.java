package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminSessionInterceptor implements HandlerInterceptor {

    public static final String STORE_SESSION_KEY = "storeId";

    private final StoreRepository storeRepository;

    public AdminSessionInterceptor(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Object storeId = request.getSession().getAttribute(STORE_SESSION_KEY);
        if (storeId instanceof Long id && storeRepository.findActiveByStoreId(id).isPresent()) {
            return true;
        }
        response.sendRedirect("/install");
        return false;
    }
}
