package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.WebhookPayload;
import br.com.nuvemcustomfields.repository.StoreRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StoreAccessBlockService {
    private final StoreRepository stores;
    private final JdbcTemplate jdbc;
    private final WebhookLifecycleService lifecycle;
    private final StorefrontCache cache;
    private final IntegrationLogService logs;

    public StoreAccessBlockService(StoreRepository stores, JdbcTemplate jdbc,
            WebhookLifecycleService lifecycle, StorefrontCache cache, IntegrationLogService logs) {
        this.stores = stores; this.jdbc = jdbc; this.lifecycle = lifecycle;
        this.cache = cache; this.logs = logs;
    }

    @Transactional
    public void forceUninstall(Long storeId) {
        var store = stores.findByStoreIdForUpdate(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (stores.isInstallationBlocked(storeId)) return;
        var now = Instant.now();
        jdbc.update("insert into store_access_blocks (store_id, blocked_at) values (?, ?)",
                storeId, Timestamp.from(now));
        store.setAccessBlockedAt(now);
        stores.saveAndFlush(store);
        lifecycle.handle(new WebhookPayload(storeId, "app/uninstalled", null));
        logs.info(storeId, "backoffice.store_access_blocked",
                "Acesso encerrado pelo backoffice. Novas instalações deste ID serão recusadas.");
        cache.invalidateStoreAfterCommit(storeId);
    }
}
