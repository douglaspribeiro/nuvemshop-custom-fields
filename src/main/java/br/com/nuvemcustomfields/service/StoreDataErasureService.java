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

    public StoreDataErasureService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void erase(Long storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("Identificador da loja nao informado.");
        }

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
        int storesDeleted = jdbcTemplate.update("delete from stores where store_id = ?", storeId);

        LOGGER.info("lgpd.store_redact.completed store_id={} store_record_deleted={}", storeId, storesDeleted > 0);
    }
}
