package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.repository.StoreRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/** Estatísticas agregadas; a exclusão da loja não mantém identificadores. */
@Service
public class StoreDepartureService {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private final StoreRepository stores;
    private final JdbcTemplate jdbc;

    public StoreDepartureService(StoreRepository stores, JdbcTemplate jdbc) {
        this.stores = stores;
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(Long storeId, boolean erasure) {
        // O mesmo lock serializa app/uninstalled e store/redact. Após a exclusão,
        // eventos atrasados/repetidos não recriam a loja nem incrementam os totais.
        stores.findByStoreIdForUpdate(storeId).ifPresent(store -> {
            if (store.isDepartureCounted()) return;
            jdbc.update("""
                    INSERT INTO store_departure_daily
                        (departure_day, uninstall_count, erasure_departure_count,
                         recovered_uninstall_count, recovered_erasure_count)
                    VALUES (?, ?, ?, 0, 0)
                    ON DUPLICATE KEY UPDATE
                        uninstall_count = uninstall_count + ?,
                        erasure_departure_count = erasure_departure_count + ?
                    """, LocalDate.now(ZONE), erasure ? 0 : 1, erasure ? 1 : 0,
                    erasure ? 0 : 1, erasure ? 1 : 0);
            store.setDepartureCounted(true);
            stores.saveAndFlush(store);
        });
    }

    public Summary summary() {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(uninstall_count + recovered_uninstall_count), 0),
                       COALESCE(SUM(erasure_departure_count + recovered_erasure_count), 0),
                       COALESCE(SUM(CASE WHEN departure_day = ?
                           THEN uninstall_count + erasure_departure_count
                             + recovered_uninstall_count + recovered_erasure_count ELSE 0 END), 0)
                FROM store_departure_daily
                """, (rs, row) -> new Summary(rs.getLong(1), rs.getLong(2), rs.getLong(3)),
                LocalDate.now(ZONE));
    }

    public record Summary(long uninstalls, long erasures, long today) {
        public long getTotal() { return uninstalls + erasures; }
    }
}
