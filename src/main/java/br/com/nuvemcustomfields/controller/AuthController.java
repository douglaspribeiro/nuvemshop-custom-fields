package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.service.NuvemshopAuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.security.SecureRandom;
import java.util.Base64;

@Controller
public class AuthController {

    private static final String OAUTH_STATE_SESSION_KEY = "oauthState";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final NuvemshopAuthService authService;

    public AuthController(NuvemshopAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/")
    public RedirectView root() {
        return new RedirectView("/admin");
    }

    @GetMapping("/install")
    public RedirectView install(HttpSession session) {
        byte[] stateBytes = new byte[24];
        SECURE_RANDOM.nextBytes(stateBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
        session.setAttribute(OAUTH_STATE_SESSION_KEY, state);
        return new RedirectView(authService.buildAuthorizationUrl(state));
    }

    @GetMapping("/oauth/callback")
    public RedirectView callback(@RequestParam String code, @RequestParam String state, HttpSession session) {
        Object expectedState = session.getAttribute(OAUTH_STATE_SESSION_KEY);
        if (!state.equals(expectedState)) {
            throw new IllegalArgumentException("Estado OAuth invalido.");
        }
        session.removeAttribute(OAUTH_STATE_SESSION_KEY);
        Store store = authService.exchangeCodeAndUpsertStore(code);
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, store.getStoreId());
        return new RedirectView("/admin");
    }
}
