package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.repository.ShopifyShopRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ShopifySessionInterceptor implements HandlerInterceptor {

    public static final String SHOP_SESSION_KEY = "shopifyShopId";

    private final ShopifyShopRepository shopRepository;

    public ShopifySessionInterceptor(ShopifyShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Object shopId = request.getSession().getAttribute(SHOP_SESSION_KEY);
        if (shopId instanceof Long id && shopRepository.findActiveById(id).isPresent()) {
            return true;
        }
        response.sendRedirect("/shopify/install");
        return false;
    }
}
