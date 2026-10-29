package br.com.nuvemcustomfields.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreDataErasureService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreDataErasureService.class);

    private final JdbcTemplate jdbcTemplate;
    private final StoreDepartureService departures;
    private final WinbackDiscountService discounts;

    @org.springframework.beans.factory.annotation.Autowired
    public StoreDataErasureService(JdbcTemplate jdbcTemplate, StoreDepartureService departures,
            WinbackDiscountService discounts) {
        this.jdbcTemplate = jdbcTemplate;
        this.departures = departures;
        this.discounts = discounts;
    }
    public StoreDataErasureService(JdbcTemplate jdbcTemplate, StoreDepartureService departures) {
        this(jdbcTemplate, departures, null);
    }

    @Transactional
    public void erase(Long storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("Identificador da loja nao informado.");
        }

        // Pode chegar antes de app/uninstalled. Conta uma saída por exclusão,
        // separada de desinstalações confirmadas, antes de apagar o cadastro.
        departures.record(storeId, true);
        jdbcTemplate.update("delete from personalization_image_references where store_id=?",storeId);
        jdbcTemplate.update("delete from template_applications where store_id=?",storeId);
        jdbcTemplate.update("delete from store_templates where store_id=?",storeId);
        // A deletion worker retains only the opaque object id and deletion intent.
        // Never keep customer session/order metadata after a permanent erasure.
        jdbcTemplate.update("update buyer_uploads set state='DELETING',delete_reason='ERASED',session_hash='',request_id=id,"
                + "original_name=null,content_type=null,product_id=0,field_id=0,order_id=null,order_item_id=null,order_created_at=null,retry_at=null where store_id=?", storeId);
        jdbcTemplate.update("delete from buyer_upload_entitlements where store_id=?", storeId);
        jdbcTemplate.update("delete from buyer_upload_sessions where store_id=?", storeId);
        // Keep only the durable S3 deletion ledger after owner data is erased.
        jdbcTemplate.update("update personalization_images set state='DELETING',field_id=null,retry_at=?,updated_at=? where store_id=?",
                java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(300)), java.sql.Timestamp.from(java.time.Instant.now()), storeId);
        if (discounts != null) discounts.beforeErasure(storeId);
        jdbcTemplate.update(
                "delete from support_messages where ticket_id in (select id from support_tickets where store_id = ?)",
                storeId
        );
        jdbcTemplate.update("delete from support_tickets where store_id = ?", storeId);
        jdbcTemplate.update("delete from feature_requests where store_id = ?", storeId);
        jdbcTemplate.update(
                "delete from personalization_fields where rule_id in (select id from personalization_rules where store_id = ?)",
                storeId
        );
        jdbcTemplate.update("delete from personalization_field_history where store_id = ?", storeId);
        jdbcTemplate.update("delete from personalization_rules where store_id = ?", storeId);
        jdbcTemplate.update("delete from store_configuration_snapshots where store_id = ?", storeId);
        jdbcTemplate.update("delete from user_journey_audit where store_id = ?", storeId);
        jdbcTemplate.update("delete from integration_logs where store_id = ?", storeId);
        jdbcTemplate.update("delete from plan_events where store_id = ?", storeId);
        jdbcTemplate.update("delete from payment_webhook_events where store_id = ?", storeId);
        jdbcTemplate.update("delete from payment_notification_outbox where store_id = ?", storeId);
        jdbcTemplate.update("delete from winback_email_events where email_id in "
                + "(select id from winback_emails where campaign_id in "
                + "(select id from winback_campaigns where store_id = ?))", storeId);
        jdbcTemplate.update("delete from winback_emails where campaign_id in "
                + "(select id from winback_campaigns where store_id = ?)", storeId);
        jdbcTemplate.update("delete from winback_coupons where store_id = ?", storeId);
        jdbcTemplate.update("delete from winback_campaigns where store_id = ?", storeId);
        jdbcTemplate.update("delete from winback_outbox where store_id = ?", storeId);
        jdbcTemplate.update("delete from payment_attempts where store_id = ?", storeId);
        jdbcTemplate.update("delete from upgrade_adjustments where store_id = ?", storeId);
        jdbcTemplate.update("delete from upgrade_coupon_uses where store_id = ?", storeId);
        jdbcTemplate.update("delete from payment_subscriptions where store_id = ?", storeId);
        jdbcTemplate.update("delete from store_order_sales where store_id = ?", storeId);
        jdbcTemplate.update("delete from store_sales_sync where store_id = ?", storeId);
        int storesDeleted = jdbcTemplate.update("delete from stores where store_id = ?", storeId);

        LOGGER.info("lgpd.store_redact.completed store_id={} store_record_deleted={}", storeId, storesDeleted > 0);
    }
}
