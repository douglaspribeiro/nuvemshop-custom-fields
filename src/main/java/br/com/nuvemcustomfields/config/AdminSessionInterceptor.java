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

    /** So escreve na sessao quando muda: evita replicar sessao a cada request. */
    private void applyStoreLocale(HttpServletRequest request, Store store) {
        Locale locale = StoreLocale.forCountry(store.getStoreCountryCode());
        if (!locale.equals(request.getSession().getAttribute(AppLocaleResolver.SESSION_KEY))) {
            request.getSession().setAttribute(AppLocaleResolver.SESSION_KEY, locale);
            LOGGER.info(
                    "admin.session.locale store_id={} country={} locale={}",
                    store.getStoreId(),
                    store.getStoreCountryCode(),
                    locale.toLanguageTag()
            );
        }
    }
}
