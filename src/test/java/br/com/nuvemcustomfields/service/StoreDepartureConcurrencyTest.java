package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:departureConcurrent;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class StoreDepartureConcurrencyTest {
    @Autowired StoreRepository stores;
    @Autowired StoreDepartureService departures;
    @Autowired StoreDataErasureService erasure;
    @Autowired WebhookLifecycleService webhooks;
    @Autowired br.com.nuvemcustomfields.repository.StoreConfigurationSnapshotRepository snapshots;
    @Autowired JdbcTemplate jdbc;

    @Test
    void simultaneousUninstallAndErasureCountOnce() throws Exception {
        Store store = new Store();
        store.setStoreId(998877665L);
        stores.saveAndFlush(store);
        long before = departures.summary().getTotal();
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var uninstall = workers.submit(() -> {
                start.await();
                departures.record(store.getStoreId(), false);
                return null;
            });
            var delete = workers.submit(() -> {
                start.await();
                erasure.erase(store.getStoreId());
                return null;
            });
            start.countDown();
            uninstall.get(20, TimeUnit.SECONDS);
            delete.get(20, TimeUnit.SECONDS);
        }
        assertThat(departures.summary().getTotal()).isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("select count(*) from stores where store_id = ?",
                Long.class, store.getStoreId())).isZero();
    }
    @Test
    void repeatedConcurrentWebhooksPreserveOneConfigurationAndDoNotRecreateErasedData() throws Exception {
        long id = 998877666L;
        Store store = new Store(); store.setStoreId(id); store.setPlan(br.com.nuvemcustomfields.entity.PlanType.PREMIUM_PLUS);
        stores.saveAndFlush(store);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await(); webhooks.handle(new br.com.nuvemcustomfields.dto.WebhookPayload(id, "app/uninstalled", null)); return null;
            });
            var duplicate = workers.submit(() -> {
                start.await(); webhooks.handle(new br.com.nuvemcustomfields.dto.WebhookPayload(id, "app/uninstalled", null)); return null;
            });
            start.countDown(); first.get(20, TimeUnit.SECONDS); duplicate.get(20, TimeUnit.SECONDS);
        }
        var copies = snapshots.findByStoreIdOrderByUninstalledAtDesc(id);
        assertThat(copies).hasSize(1);
        assertThat(copies.getFirst().getUninstalledAt()).isEqualTo(stores.findByStoreId(id).orElseThrow().getUninstalledAt());
        assertThat(copies.getFirst().getConfigurationJson()).contains("PREMIUM_PLUS");
        erasure.erase(id);
        webhooks.handle(new br.com.nuvemcustomfields.dto.WebhookPayload(id, "app/uninstalled", null));
        assertThat(snapshots.findByStoreIdOrderByUninstalledAtDesc(id)).isEmpty();
    }

}
