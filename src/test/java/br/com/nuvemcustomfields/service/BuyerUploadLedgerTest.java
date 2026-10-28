package br.com.nuvemcustomfields.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static br.com.nuvemcustomfields.service.BuyerUploadPolicy.*;
import static org.junit.jupiter.api.Assertions.*;

class BuyerUploadLedgerTest {
    private JdbcTemplate jdbc;
    private BuyerUploadLedger ledger;
    private TransactionTemplate tx;
    private static final String SESSION = "a".repeat(64);

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table stores (store_id bigint primary key, plan varchar(30), billing_suspended boolean default false, uninstalled_at timestamp, erasure_requested_at timestamp)");
        jdbc.execute("create table payment_subscriptions (store_id bigint primary key, access_active boolean, provider_environment varchar(30))");
        jdbc.execute("create table personalization_rules (id bigint primary key, store_id bigint, product_id bigint)");
        jdbc.execute("create table personalization_fields (id bigint primary key, rule_id bigint)");
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V64__buyer_upload_ledger.sql"));
        }
        jdbc.update("insert into stores(store_id,plan) values (1,'PREMIUM_PLUS'),(2,'PREMIUM_ULTRA')");
        jdbc.update("insert into personalization_rules values (10,1,100),(20,2,200)");
        jdbc.update("insert into personalization_fields values (11,10),(22,20)");
        jdbc.update("insert into buyer_upload_entitlements values (1,?,?,true,?)", OFFER, PRO_BYTES, Timestamp.from(Instant.now()));
        ledger = new BuyerUploadLedger(jdbc);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    }
    private BuyerUploadLedger.Entry reserve(long bytes) {
        return tx.execute(status -> ledger.reserve(1,100,11,SESSION,UUID.randomUUID().toString(),bytes));
    }
    private void run(Runnable action) { tx.executeWithoutResult(status -> action.run()); }
    private void stored(BuyerUploadLedger.Entry file) { run(() -> ledger.stored(1,file.id(),SESSION,file.sizeBytes())); }
    private void cart(BuyerUploadLedger.Entry file) { stored(file); run(() -> ledger.attachCart(1,file.id(),SESSION)); }

    @Test void existingPlansDoNotGrantEntitlementAndSuspensionBlocksNewUploads() {
        assertFalse(ledger.usage(2).acceptsUploads());
        jdbc.update("update stores set billing_suspended=true where store_id=1");
        assertThrows(BuyerUploadLedger.Rejected.class, () -> reserve(1));
        jdbc.update("update stores set billing_suspended=false,plan='PREMIUM' where store_id=1");
        assertThrows(BuyerUploadLedger.Rejected.class, () -> reserve(1));
    }
    @Test void reservesRealBytesAndRetriesDoNotConsumeSpaceTwice() {
        String request = UUID.randomUUID().toString();
        var first = tx.execute(status -> ledger.reserve(1,100,11,SESSION,request,123));
        var retry = tx.execute(status -> ledger.reserve(1,100,11,SESSION,request,123));
        assertEquals(first.id(), retry.id());
        assertEquals(123, ledger.usage(1).occupiedBytes());
        assertThrows(BuyerUploadLedger.Rejected.class, () -> tx.execute(status -> ledger.reserve(1,100,11,SESSION,request,124)));
    }
    @Test void rejectsInvalidSizesAndCrossStoreFields() {
        for (long size : new long[]{0,-1,MAX_FILE_BYTES+1}) assertThrows(BuyerUploadLedger.Rejected.class, () -> reserve(size));
        assertThrows(BuyerUploadLedger.Rejected.class, () -> tx.execute(status -> ledger.reserve(1,200,22,SESSION,UUID.randomUUID().toString(),1)));
        assertEquals(0,ledger.usage(1).occupiedBytes());
    }
    @Test void anotherSessionCannotCompleteAttachOrRemove() {
        var file = reserve(100);
        String stranger = "b".repeat(64);
        assertThrows(BuyerUploadLedger.Rejected.class, () -> run(() -> ledger.stored(1,file.id(),stranger,100)));
        assertThrows(BuyerUploadLedger.Rejected.class, () -> run(() -> ledger.removeTemporary(1,file.id(),stranger)));
        stored(file);
        assertThrows(BuyerUploadLedger.Rejected.class, () -> run(() -> ledger.attachCart(1,file.id(),stranger)));
        assertThrows(BuyerUploadLedger.Rejected.class, () -> run(() -> ledger.stored(2,file.id(),SESSION,100)));
    }
    @Test void incompleteAndMismatchedUploadsCannotReachCart() {
        var file = reserve(100);
        assertThrows(BuyerUploadLedger.Rejected.class, () -> run(() -> ledger.attachCart(1,file.id(),SESSION)));
        assertThrows(BuyerUploadLedger.Rejected.class, () -> run(() -> ledger.stored(1,file.id(),SESSION,99)));
        stored(file);
        stored(file);
        assertEquals(100, ledger.usage(1).occupiedBytes());
    }
    @Test void orderedRetentionComesFromOrderAndCannotBeRebound() {
        var first = reserve(100);
        var second = reserve(200);
        cart(first); cart(second);
        Instant created = Instant.now().minusSeconds(86400);
        run(() -> ledger.confirmOrder(1,first.id(),100,11,300,301,created));
        run(() -> ledger.confirmOrder(1,second.id(),100,11,300,302,created));
        run(() -> ledger.confirmOrder(1,first.id(),100,11,300,301,created));
        assertEquals(created.plus(ORDER_RETENTION).getEpochSecond(),jdbc.queryForObject("select expires_at from buyer_uploads where id=?", Timestamp.class,first.id()).toInstant().getEpochSecond());
        assertThrows(BuyerUploadLedger.Rejected.class, () -> run(() -> ledger.confirmOrder(1,first.id(),100,11,300,302,created)));
        assertThrows(BuyerUploadLedger.Rejected.class, () -> run(() -> ledger.removeTemporary(1,first.id(),SESSION)));
    }
    @Test void delayedConfirmationWorksUntilDeletionStarts() {
        var file = reserve(100); cart(file);
        jdbc.update("update buyer_uploads set expires_at=? where id=?",Timestamp.from(Instant.now().minusSeconds(60)),file.id());
        run(() -> ledger.confirmOrder(1,file.id(),100,11,300,301,Instant.now()));
        assertEquals("ORDERED",jdbc.queryForObject("select state from buyer_uploads where id=?",String.class,file.id()));
        var expired = reserve(100); cart(expired);
        run(() -> ledger.expire(1,expired.id(),Instant.now().plusSeconds(90000)));
        assertThrows(BuyerUploadLedger.Rejected.class, () -> run(() -> ledger.confirmOrder(1,expired.id(),100,11,300,302,Instant.now())));
    }
    @Test void deletionOnlyReleasesQuotaAfterStorageAcknowledgment() {
        var file = reserve(100);
        run(() -> ledger.removeTemporary(1,file.id(),SESSION));
        assertEquals(100,ledger.usage(1).occupiedBytes());
        run(() -> ledger.acknowledgeDeletion(1,file.id()));
        run(() -> ledger.acknowledgeDeletion(1,file.id()));
        assertEquals(0,ledger.usage(1).occupiedBytes());
        assertEquals("DELETED",jdbc.queryForObject("select state from buyer_uploads where id=?",String.class,file.id()));
    }
    @Test void expiryIsIdempotentAndKeepsTombstone() {
        var file = reserve(100);
        run(() -> ledger.expire(1,file.id(),Instant.now()));
        assertEquals("RESERVED",jdbc.queryForObject("select state from buyer_uploads where id=?",String.class,file.id()));
        run(() -> ledger.expire(1,file.id(),Instant.now().plusSeconds(90000)));
        run(() -> ledger.acknowledgeDeletion(1,file.id()));
        run(() -> ledger.expire(1,file.id(),Instant.now().plusSeconds(90000)));
        assertEquals("EXPIRED",jdbc.queryForObject("select state from buyer_uploads where id=?",String.class,file.id()));
    }
    @Test void downgradeAndUninstallDoNotDeleteExistingOrders() {
        var file = reserve(100); cart(file);
        run(() -> ledger.confirmOrder(1,file.id(),100,11,300,301,Instant.now()));
        jdbc.update("update stores set plan='FREE',uninstalled_at=? where store_id=1",Timestamp.from(Instant.now()));
        assertThrows(BuyerUploadLedger.Rejected.class, () -> reserve(100));
        assertEquals(100,ledger.usage(1).occupiedBytes());
        assertEquals("ORDERED",jdbc.queryForObject("select state from buyer_uploads where id=?",String.class,file.id()));
    }
    @Test void shopperCannotDeleteArtworkAlreadySubmittedToCart() {
        var file=reserve(100);cart(file);
        run(()->ledger.removeTemporary(1,file.id(),SESSION));
        assertEquals("CART",jdbc.queryForObject("select state from buyer_uploads where id=?",String.class,file.id()));
        assertEquals(100,ledger.usage(1).occupiedBytes());
    }
    @Test void concurrentReservationsCannotExceedQuota() throws Exception {
        // Occupy everything except one maximum-sized file, without allocating files.
        var existing = reserve(1);
        jdbc.update("update buyer_uploads set size_bytes=? where id=?",PRO_BYTES-MAX_FILE_BYTES,existing.id());
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); try { reserve(MAX_FILE_BYTES); return true; } catch (BuyerUploadLedger.Rejected e) { return false; } });
            var b = pool.submit(() -> { start.await(); try { reserve(MAX_FILE_BYTES); return true; } catch (BuyerUploadLedger.Rejected e) { return false; } });
            start.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        assertEquals(PRO_BYTES,ledger.usage(1).occupiedBytes());
    }
    @Test void thresholdsUseApprovedDecimalUnits() {
        assertEquals(0,warningPercent(PRO_BYTES*79/100,PRO_BYTES));
        assertEquals(80,warningPercent(PRO_BYTES*80/100,PRO_BYTES));
        assertEquals(95,warningPercent(PRO_BYTES*95/100,PRO_BYTES));
        assertEquals(100,warningPercent(PRO_BYTES,PRO_BYTES));
    }
}
