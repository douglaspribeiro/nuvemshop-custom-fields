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
        if (discounts != null) discounts.beforeErasure(storeId);
        jdbcTemplate.update(
                "delete from support_messages where ticket_id in (select id from support_tickets where store_id = ?)",
                storeId
        );
        jdbcTemplate.update("delete from support_tickets where store_id = ?", storeId);
        jdbcTemplate.update(
                "delete from personalization_fields where rule_id in (select id from personalization_rules where store_id = ?)",
                storeId
        );
        jdbcTemplate.update("delete from personalization_rules where store_id = ?", storeId);
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
        jdbcTemplate.update("delete from payment_subscriptions where store_id = ?", storeId);
        jdbcTemplate.update("delete from store_order_sales where store_id = ?", storeId);
        jdbcTemplate.update("delete from store_sales_sync where store_id = ?", storeId);
        int storesDeleted = jdbcTemplate.update("delete from stores where store_id = ?", storeId);

        LOGGER.info("lgpd.store_redact.completed store_id={} store_record_deleted={}", storeId, storesDeleted > 0);
    }
}
