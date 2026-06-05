package br.com.nuvemcustomfields.shopify;

import br.com.nuvemcustomfields.config.ShopifySessionInterceptor;
import br.com.nuvemcustomfields.entity.ShopifyShop;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ShopifyAuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShopifyAuthController.class);
    private static final String STATE_SESSION_KEY = "shopifyOAuthState";

    private final ShopifyAuthService authService;
    private final ShopifySecurityService securityService;

    public ShopifyAuthController(ShopifyAuthService authService, ShopifySecurityService securityService) {
        this.authService = authService;
        this.securityService = securityService;
    }

    @GetMapping("/shopify/install")
    public String install(@RequestParam(required = false) String shop, HttpSession session, Model model) {
        LOGGER.info("shopify.install.open shop_present={}", shop != null && !shop.isBlank());
        if (shop == null || shop.isBlank()) {
            return "shopify/install";
        }
        String state = authService.newState();
        session.setAttribute(STATE_SESSION_KEY, state);
        return "redirect:" + authService.authorizationUrl(shop, state);
    }

    @GetMapping("/shopify/oauth/callback")
    public String callback(
            @RequestParam String shop,
            @RequestParam String code,
            @RequestParam String state,
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        LOGGER.info("shopify.oauth.callback shop={}", shop);
        Object expectedState = session.getAttribute(STATE_SESSION_KEY);
        if (!state.equals(expectedState)) {
            redirectAttributes.addFlashAttribute("error", "Sessao OAuth Shopify invalida.");
            return "redirect:/shopify/install";
        }
        if (!securityService.validHmac(request.getParameterMap())) {
            redirectAttributes.addFlashAttribute("error", "Assinatura Shopify invalida.");
            return "redirect:/shopify/install";
        }
        ShopifyShop installedShop = authService.exchangeCodeAndUpsertShop(shop, code);
        session.setAttribute(ShopifySessionInterceptor.SHOP_SESSION_KEY, installedShop.getId());
        session.removeAttribute(STATE_SESSION_KEY);
        return "redirect:/shopify/admin";
    }
}
