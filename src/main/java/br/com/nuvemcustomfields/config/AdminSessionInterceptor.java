package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.i18n.AppLocaleResolver;
import br.com.nuvemcustomfields.i18n.StoreLocale;
import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import java.time.Instant;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;
import java.util.Optional;

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
            Optional<Store> activeStore = storeRepository.findActiveByStoreId(id);
            LOGGER.info(
                    "admin.session.check uri={} store_id={} active_store_found={}",
                    request.getRequestURI(),
                    id,
                    activeStore.isPresent()
            );
            if (activeStore.isPresent()) {
                recordMerchantAccess(request, handler, activeStore.get());
                applyStoreLocale(request, activeStore.get());
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
        LOGGER.warn("admin.session.redirect_embedded uri={}", request.getRequestURI());
        response.sendRedirect("/admin/embedded");
        return false;
    }

    private void recordMerchantAccess(HttpServletRequest request, Object handler, Store store) {
        if (Boolean.TRUE.equals(request.getSession().getAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY))) return;
        if (!(handler instanceof HandlerMethod method) || method.getMethod().getReturnType() != String.class
                || method.hasMethodAnnotation(ResponseBody.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), ResponseBody.class)) return;
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(60);
        if (store.getLastAdminAccessAt() != null && store.getLastAdminAccessAt().isAfter(cutoff)) return;
        // One write at most per minute per store, guarded in SQL for concurrent requests.
        if (storeRepository.recordAdminAccess(store.getStoreId(), now, cutoff) > 0) store.setLastAdminAccessAt(now);
    }

    /** So escreve na sessao quando muda: evita replicar sessao a cada request. */
    private void applyStoreLocale(HttpServletRequest request, Store store) {
        Locale locale = StoreLocale.forCountry(store.getStoreCountryCode());
        if (!locale.equals(request.getSession().getAttribute(AppLocaleResolver.SESSION_KEY))) {
            request.getSession().setAttribute(AppLocaleResolver.SESSION_KEY, locale);
            LOGGER.info(
                    "admin.session.locale.applied store_id={} country={} locale={} uri={}",
                    store.getStoreId(),
                    store.getStoreCountryCode(),
                    locale.toLanguageTag(),
                    request.getRequestURI()
            );
        }
    }
}
