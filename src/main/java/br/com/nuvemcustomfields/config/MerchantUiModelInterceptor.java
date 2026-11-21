package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.entity.MerchantInterfacePreference.Choice;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.service.MerchantInterfaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.WebUtils;

@Component
public class MerchantUiModelInterceptor implements HandlerInterceptor {
    private static final String CSRF = "merchantUiCsrf";
    private final MerchantInterfaceService preferences;

    public MerchantUiModelInterceptor(MerchantInterfaceService preferences) {
        this.preferences = preferences;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView view) {
        if (view == null || view.getViewName() == null) return;

        HttpSession session = request.getSession(false);
        String name = view.getViewName();
        Long storeId;
        if (name.startsWith("admin/") && !name.equals("admin/embedded")) {
            storeId = storeId(session);
        } else if (name.equals("public/support") || name.equals("public/support-ticket")) {
            storeId = view.getModel().get("store") instanceof Store store ? store.getStoreId() : null;
        } else {
            return;
        }
        boolean eligible = storeId != null && preferences.isEligible(storeId);
        Choice choice = eligible ? preferences.getChoice(storeId).orElse(null) : null;
        boolean canChoose = eligible && !isOperator(session);

        view.addObject("merchantUiEligible", eligible);
        view.addObject("newMerchantUi", eligible && choice == Choice.PREVIEW);
        view.addObject("merchantUiInvite", canChoose && choice == null);
        if (canChoose) view.addObject(CSRF, issueToken(session, storeId));
    }

    public static Long storeId(HttpSession session) {
        if (session != null && session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY) instanceof Long id) {
            return id;
        }
        return null;
    }

    public static boolean isOperator(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY));
    }

    private static String issueToken(HttpSession session, long storeId) {
        synchronized (WebUtils.getSessionMutex(session)) {
            String prefix = storeId + ":";
            if (session.getAttribute(CSRF) instanceof String existing && existing.startsWith(prefix)) {
                return existing;
            }
            String token = prefix + UUID.randomUUID();
            session.setAttribute(CSRF, token);
            return token;
        }
    }

    public static boolean validToken(HttpSession session, long storeId, String token) {
        return token != null && session.getAttribute(CSRF) instanceof String expected
                && expected.startsWith(storeId + ":")
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }
}
