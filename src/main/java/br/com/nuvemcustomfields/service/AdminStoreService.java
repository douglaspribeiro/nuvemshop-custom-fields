package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AdminStoreService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminStoreService.class);

    private final StoreRepository storeRepository;

    public AdminStoreService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
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
}
