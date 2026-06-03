package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class AdminStoreService {

    private final StoreRepository storeRepository;

    public AdminStoreService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    public Store requireCurrentStore(HttpSession session) {
        Long storeId = (Long) session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
        return storeRepository.findActiveByStoreId(storeId)
                .orElseThrow(() -> new IllegalStateException("Loja ativa nao encontrada na sessao."));
    }
}
