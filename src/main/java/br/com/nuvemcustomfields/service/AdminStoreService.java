package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.i18n.Messages;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AdminStoreService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminStoreService.class);
    private static final String HEX_COLOR_PATTERN = "^#[0-9A-Fa-f]{6}$";

    private final StoreRepository storeRepository;

    private final Messages messages;

    public AdminStoreService(StoreRepository storeRepository, Messages messages) {
        this.storeRepository = storeRepository;
        this.messages = messages;
    }

    public Store requireCurrentStore(HttpSession session) {
        Long storeId = (Long) session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
        LOGGER.info("admin.store.require session_id={} store_id={}", session.getId(), storeId);
        return storeRepository.findActiveByStoreId(storeId)
                .orElseThrow(() -> {
                    LOGGER.error("admin.store.not_found session_id={} store_id={}", session.getId(), storeId);
                    return new IllegalStateException("Loja ativa nao encontrada na sessao.");
                });
    }

    @Transactional
    public void markCurrentStoreDisconnected(HttpSession session) {
        Object storeId = session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
        if (storeId instanceof Long id) {
            storeRepository.findByStoreId(id).ifPresent(store -> {
                store.setUninstalledAt(Instant.now());
                storeRepository.save(store);
                LOGGER.warn("admin.store.disconnected store_id={} reason=invalid_access_token", id);
            });
        }
        session.removeAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
    }

    @Transactional
    public void updateStyleSettings(
            Store store,
            String productTextColor,
            boolean clearProductTextColor,
            String checkoutTextColor,
            boolean clearCheckoutTextColor,
            String cartTextColor,
            boolean clearCartTextColor
    ) {
        Store managedStore = storeRepository.findActiveByStoreId(store.getStoreId())
                .orElseThrow(() -> new IllegalStateException("Loja ativa nao encontrada."));
        managedStore.setProductTextColor(normalizeColor(productTextColor, clearProductTextColor));
        managedStore.setCheckoutTextColor(normalizeColor(checkoutTextColor, clearCheckoutTextColor));
        managedStore.setCartTextColor(normalizeColor(cartTextColor, clearCartTextColor));
        storeRepository.save(managedStore);
        LOGGER.info(
                "admin.store.style.update store_id={} product_text_color={} checkout_text_color={} cart_text_color={}",
                managedStore.getStoreId(),
                managedStore.getProductTextColor(),
                managedStore.getCheckoutTextColor(),
                managedStore.getCartTextColor()
        );
    }

    private String normalizeColor(String value, boolean clear) {
        if (clear || value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (!normalized.matches(HEX_COLOR_PATTERN)) {
            throw new IllegalArgumentException(messages.get("error.color.invalid"));
        }
        return normalized.toUpperCase();
    }
}
