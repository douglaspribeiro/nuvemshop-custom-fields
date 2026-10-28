package br.com.nuvemcustomfields.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Persistent quota reservations. Every mutation locks the store before the file,
 * sharing the same serialization boundary as configuration and erasure.
 * No method in this ledger is exposed directly to an anonymous controller.
 */
@Service
public class BuyerUploadLedger {
    public enum State { RESERVED, TEMPORARY, CART, ORDERED, DELETING, DELETED, EXPIRED }
    public record Entry(String id, long storeId, long productId, long fieldId,
                        long sizeBytes, State state, Long orderId, Long orderItemId,
                        Instant expiresAt) {}
    public record Usage(long occupiedBytes, long quotaBytes, int warningPercent, boolean acceptsUploads) {}
    public static class Rejected extends IllegalArgumentException {
        public Rejected(String code) { super(code); }
    }
    private final JdbcTemplate jdbc;
    public BuyerUploadLedger(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Entry reserve(long storeId, long productId, long fieldId, String sessionToken,
                         String requestId, long sizeBytes) {
        requireSession(sessionToken);
        requireUuid(requestId);
        if (sizeBytes <= 0 || sizeBytes > BuyerUploadPolicy.MAX_FILE_BYTES) throw new Rejected("upload.size.invalid");
        lockStore(storeId);
        var usage = usageLocked(storeId);
        if (!usage.acceptsUploads()) throw new Rejected("upload.unavailable");
        String hash = hash(sessionToken);
        var existing = jdbc.query("select * from buyer_uploads where store_id=? and session_hash=? and request_id=?",
                this::entry, storeId, hash, requestId);
        if (!existing.isEmpty()) {
            var file = existing.getFirst();
            if (file.productId() != productId || file.fieldId() != fieldId || file.sizeBytes() != sizeBytes
                    || (file.state() != State.RESERVED && file.state() != State.TEMPORARY && file.state() != State.CART)
                    || !file.expiresAt().isAfter(Instant.now()))
                throw new Rejected("upload.request.conflict");
            return file;
        }
        // Configuration authorization belongs to the upload orchestrator. The ledger
        // still checks ownership to prevent cross-product or cross-store reservations.
        Long fields = jdbc.queryForObject("select count(*) from personalization_fields f join personalization_rules r on r.id=f.rule_id "
                + "where f.id=? and r.store_id=? and r.product_id=?", Long.class, fieldId, storeId, productId);
        if (fields == null || fields != 1) throw new Rejected("upload.field.invalid");
        if (sizeBytes > usage.quotaBytes() - usage.occupiedBytes()) throw new Rejected("upload.quota.exceeded");
        Long attempts=jdbc.queryForObject("select count(*) from buyer_uploads where store_id=? and created_at>?",Long.class,
                storeId,ts(Instant.now().minusSeconds(86400)));
        if(attempts!=null && attempts>=1000) throw new Rejected("upload.rate.limit");
        // Bound concurrent/abandoned reservations even for tiny files.
        Long pending = jdbc.queryForObject("select count(*) from buyer_uploads where store_id=? and session_hash=? "
                + "and state in ('RESERVED','TEMPORARY','CART')", Long.class, storeId, hash);
        if (pending != null && pending >= 20) throw new Rejected("upload.session.limit");
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now(), expires = now.plus(BuyerUploadPolicy.TEMPORARY_RETENTION);
        jdbc.update("insert into buyer_uploads (id,store_id,product_id,field_id,session_hash,request_id,size_bytes,state,created_at,expires_at) "
                + "values (?,?,?,?,?,?,?,'RESERVED',?,?)", id, storeId, productId, fieldId, hash, requestId, sizeBytes, ts(now), ts(expires));
        return new Entry(id, storeId, productId, fieldId, sizeBytes, State.RESERVED, null, null, expires);
    }

    /** Invoke only after a private object has been fully stored and validated. */
    @Transactional
    public void stored(long storeId, String id, String sessionToken, long actualBytes) {
        lockStore(storeId);
        Entry file = owned(storeId, id, sessionToken);
        if (!usageLocked(storeId).acceptsUploads()) throw new Rejected("upload.unavailable");
        if (file.sizeBytes() != actualBytes) throw new Rejected("upload.size.mismatch");
        if (file.state() == State.TEMPORARY && file.expiresAt().isAfter(Instant.now())) return;
        requireActive(file, State.RESERVED);
        jdbc.update("update buyer_uploads set state='TEMPORARY' where id=?", id);
    }

    @Transactional
    public void validateTemporary(long storeId,String id,String sessionToken) {
        lockStore(storeId);
        var file=owned(storeId,id,sessionToken);
        if(!usageLocked(storeId).acceptsUploads())throw new Rejected("upload.unavailable");
        if(file.state()!=State.TEMPORARY && file.state()!=State.CART)throw new Rejected("upload.not.ready");
        requireUnexpired(file);
        jdbc.update("update buyer_uploads set expires_at=? where id=?",ts(Instant.now().plus(BuyerUploadPolicy.TEMPORARY_RETENTION)),id);
    }

    @Transactional
    public void attachCart(long storeId, String id, String sessionToken) {
        lockStore(storeId);
        var file = owned(storeId, id, sessionToken);
        if (!usageLocked(storeId).acceptsUploads()) throw new Rejected("upload.unavailable");
        if (file.state() != State.TEMPORARY && file.state() != State.CART) throw new Rejected("upload.not.ready");
        requireUnexpired(file);
        jdbc.update("update buyer_uploads set state='CART',expires_at=? where id=?",
                ts(Instant.now().plus(BuyerUploadPolicy.TEMPORARY_RETENTION)), id);
    }

    /** Called exclusively after retrieving authoritative order/item data server-side. */
    @Transactional
    public void confirmOrder(long storeId, String id, long productId, long fieldId,
                             long orderId, long itemId, Instant orderCreatedAt) {
        if (orderId <= 0 || itemId <= 0 || orderCreatedAt == null || orderCreatedAt.isAfter(Instant.now().plusSeconds(300)))
            throw new Rejected("upload.order.invalid");
        lockStore(storeId);
        var file = find(storeId, id);
        if (file.productId() != productId || file.fieldId() != fieldId) throw new Rejected("upload.order.invalid");
        if (file.state() == State.ORDERED) {
            if (!Long.valueOf(orderId).equals(file.orderId()) || !Long.valueOf(itemId).equals(file.orderItemId()))
                throw new Rejected("upload.order.conflict");
            return;
        }
        if (file.state() != State.CART) throw new Rejected("upload.not.ready");
        // Delayed synchronization may confirm a CART file past its temporary deadline
        // only while deletion has not started. ORDERED expiry is always order-based.
        Instant expiry = orderCreatedAt.plus(BuyerUploadPolicy.ORDER_RETENTION);
        if (!expiry.isAfter(Instant.now())) throw new Rejected("upload.expired");
        jdbc.update("update buyer_uploads set state='ORDERED',order_id=?,order_item_id=?,order_created_at=?,expires_at=? where id=?",
                orderId, itemId, ts(orderCreatedAt), ts(expiry), id);
    }

    @Transactional
    public void removeTemporary(long storeId, String id, String sessionToken) {
        lockStore(storeId);
        var file = owned(storeId, id, sessionToken);
        if (file.state() == State.DELETING || file.state() == State.DELETED) return;
        // Once submitted to a cart, the shopper may clear their local selection,
        // but cannot delete artwork potentially referenced by that cart/order.
        // Reconciliation and the 24h expiry resolve abandoned cart objects.
        if(file.state()==State.CART)return;
        if (file.state() == State.ORDERED || file.state() == State.EXPIRED) throw new Rejected("upload.not.temporary");
        queueDelete(id, "REMOVED");
    }

    @Transactional
    public void removeOrdered(long storeId, String id) {
        lockStore(storeId);
        var file = find(storeId, id);
        if (file.state() == State.DELETED || file.state() == State.EXPIRED || file.state() == State.DELETING) return;
        if (file.state() != State.ORDERED) throw new Rejected("upload.not.ordered");
        queueDelete(id, "REMOVED");
    }

    @Transactional(readOnly = true)
    public Usage usage(long storeId) { return usageLocked(storeId); }

    /** Expiry is an intent: quota remains occupied until storage deletion succeeds. */
    @Transactional
    public void expire(long storeId, String id, Instant now) {
        lockStoreIfPresent(storeId);
        var file = find(storeId, id);
        if (file.state() == State.DELETING || file.state() == State.EXPIRED || file.state() == State.DELETED) return;
        if (!file.expiresAt().isAfter(now)) queueDelete(id, "EXPIRED");
    }

    /** Storage worker must have removed all object versions before acknowledging. */
    @Transactional
    public void acknowledgeDeletion(long storeId, String id) {
        // Erasure may have removed the store already; lock the durable ledger row too.
        lockStoreIfPresent(storeId);
        find(storeId, id);
        jdbc.update("update buyer_uploads set state=case when delete_reason='EXPIRED' then 'EXPIRED' else 'DELETED' end "
                + "where store_id=? and id=? and state='DELETING'", storeId, id);
    }

    private Usage usageLocked(long storeId) {
        var quotas = jdbc.queryForList("select case when s.plan='PREMIUM_PLUS' then ? else ? end from stores s "
                + "left join buyer_upload_entitlements e on e.store_id=s.store_id "
                + "left join payment_subscriptions p on p.store_id=s.store_id "
                + "where s.store_id=? and s.uninstalled_at is null and s.erasure_requested_at is null and s.billing_suspended=false "
                + "and s.plan in ('PREMIUM_PLUS','PREMIUM_ULTRA') "
                + "and ((e.offer_code=? and e.enabled=true and e.quota_bytes in (?,?)) "
                + "or (p.buyer_upload_offer=? and p.access_active=true and p.provider_environment='PRODUCTION'))",
                Long.class, BuyerUploadPolicy.PRO_BYTES, BuyerUploadPolicy.ULTRA_BYTES, storeId,
                BuyerUploadPolicy.OFFER, BuyerUploadPolicy.PRO_BYTES, BuyerUploadPolicy.ULTRA_BYTES, BuyerUploadPolicy.OFFER);
        long occupied = jdbc.queryForObject("select coalesce(sum(size_bytes),0) from buyer_uploads where store_id=? "
                + "and state not in ('DELETED','EXPIRED')", Long.class, storeId);
        long quota = quotas.isEmpty() ? 0 : quotas.getFirst();
        return new Usage(occupied, quota, BuyerUploadPolicy.warningPercent(occupied, quota), quota > 0);
    }
    private void lockStore(long storeId) {
        if (!lockStoreIfPresent(storeId)) throw new Rejected("upload.unavailable");
    }
    private boolean lockStoreIfPresent(long storeId) {
        return !jdbc.queryForList("select store_id from stores where store_id=? for update", storeId).isEmpty();
    }
    private Entry owned(long storeId, String id, String sessionToken) {
        requireSession(sessionToken);
        var rows = jdbc.query("select * from buyer_uploads where store_id=? and id=? and session_hash=? for update",
                this::entry, storeId, id, hash(sessionToken));
        if (rows.isEmpty()) throw new Rejected("upload.not.found");
        return rows.getFirst();
    }
    private Entry find(long storeId, String id) {
        List<Entry> rows = jdbc.query("select * from buyer_uploads where store_id=? and id=? for update", this::entry, storeId, id);
        if (rows.isEmpty()) throw new Rejected("upload.not.found");
        return rows.getFirst();
    }
    private void requireActive(Entry file, State state) {
        if (file.state() != state) throw new Rejected("upload.not.ready");
        requireUnexpired(file);
    }
    private void requireUnexpired(Entry file) {
        if (!file.expiresAt().isAfter(Instant.now())) throw new Rejected("upload.expired");
    }
    private void queueDelete(String id, String reason) {
        jdbc.update("update buyer_uploads set state='DELETING',delete_reason=? where id=?", reason, id);
    }
    private static void requireUuid(String id) {
        try { if (!UUID.fromString(id).toString().equals(id)) throw new Rejected("upload.request.invalid"); }
        catch (IllegalArgumentException | NullPointerException e) { throw new Rejected("upload.request.invalid"); }
    }
    private static void requireSession(String token) {
        if (token == null || token.length() < 32 || token.length() > 256) throw new Rejected("upload.session.invalid");
    }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static Timestamp ts(Instant time) { return Timestamp.from(time); }
    private Entry entry(ResultSet row, int index) throws SQLException {
        return new Entry(row.getString("id"), row.getLong("store_id"), row.getLong("product_id"), row.getLong("field_id"),
                row.getLong("size_bytes"), State.valueOf(row.getString("state")), row.getObject("order_id", Long.class),
                row.getObject("order_item_id", Long.class), row.getTimestamp("expires_at").toInstant());
    }
}
