package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.WebhookPayload;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class StoreDepartureServiceTest {
    private static final long ID = 998877663L;
    @Autowired StoreRepository stores;
    @Autowired StoreDepartureService departures;
    @Autowired WebhookLifecycleService lifecycle;
    @Autowired StoreDataErasureService erasure;
    @Autowired JdbcTemplate jdbc;
    private StoreDepartureService.Summary before;

    @BeforeEach
    void setUp() {
        before = departures.summary();
        Store store = new Store();
        store.setStoreId(ID);
        store.setStoreName("Nome que deve ser apagado");
        store.setStoreEmail("apagar@example.com");
        store.setAccessToken("apagar-token");
        stores.saveAndFlush(store);
    }

    @Test
    void uninstallThenErasurePreservesOnlyAnonymousCountsAndIgnoresRetries() {
        uninstall();
        var timestamp = stores.findByStoreId(ID).orElseThrow().getUninstalledAt();
        uninstall();
        assertThat(stores.findByStoreId(ID).orElseThrow().getUninstalledAt()).isEqualTo(timestamp);
        erasure.erase(ID);
        erasure.erase(ID);
        uninstall();

        assertErased();
        var after = departures.summary();
        assertThat(after.uninstalls()).isEqualTo(before.uninstalls() + 1);
        assertThat(after.erasures()).isEqualTo(before.erasures());
        assertThat(after.today()).isEqualTo(before.today() + 1);
    }

    @Test
    void erasureThenUninstallCountsOneErasureDepartureWithoutRecreatingStore() {
        erasure.erase(ID);
        uninstall();
        uninstall();
        erasure.erase(ID);

        assertErased();
        var after = departures.summary();
        assertThat(after.uninstalls()).isEqualTo(before.uninstalls());
        assertThat(after.erasures()).isEqualTo(before.erasures() + 1);
        assertThat(after.getTotal()).isEqualTo(before.getTotal() + 1);
    }

    @Test
    void anotherInstallationCanCountAnotherDeparture() {
        uninstall();
        Store store = stores.findByStoreId(ID).orElseThrow();
        store.setUninstalledAt(null);
        store.setDepartureCounted(false);
        store.setAccessToken("novo-token");
        stores.saveAndFlush(store);
        uninstall();

        assertThat(departures.summary().getTotal()).isEqualTo(before.getTotal() + 2);
    }

    private void uninstall() {
        lifecycle.handle(new WebhookPayload(ID, "app/uninstalled", 123L));
    }

    private void assertErased() {
        assertThat(jdbc.queryForObject("select count(*) from stores where store_id = ?", Long.class, ID)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from integration_logs where store_id = ?", Long.class, ID)).isZero();
        assertThat(jdbc.queryForList("select * from store_departure_daily"))
                .allSatisfy(row -> assertThat(row).containsOnlyKeys(
                        "departure_day", "uninstall_count", "erasure_departure_count",
                        "recovered_uninstall_count", "recovered_erasure_count"));
    }
}
