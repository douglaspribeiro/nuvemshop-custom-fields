package br.com.nuvemcustomfields.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StoreDataErasureServiceIntegrationTest {

    private static final long STORE_ID = 9_876_543_210L;

    @Autowired
    private StoreDataErasureService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void permanentlyErasesStoreAndAllDependentData() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "insert into stores (store_id, access_token, plan, courtesy_premium, billing_suspended, installed_at) values (?, ?, ?, ?, ?, ?)",
                STORE_ID, "secret-token", "PREMIUM", false, false, now
        );
        jdbcTemplate.update(
                "insert into personalization_rules (store_id, product_id, enabled, created_at) values (?, ?, ?, ?)",
                STORE_ID, 456L, true, now
        );
        Long ruleId = jdbcTemplate.queryForObject(
                "select id from personalization_rules where store_id = ?",
                Long.class,
                STORE_ID
        );
        jdbcTemplate.update(
                "insert into personalization_fields (rule_id, label, field_type, required, max_length, sort_order) values (?, ?, ?, ?, ?, ?)",
                ruleId, "Nome", "TEXT", true, 100, 0
        );
        jdbcTemplate.update(
                "insert into integration_logs (store_id, level, event_type, message, created_at) values (?, ?, ?, ?, ?)",
                STORE_ID, "INFO", "test", "test", now
        );
        jdbcTemplate.update(
                "insert into plan_events (store_id, to_plan, source, created_at) values (?, ?, ?, ?)",
                STORE_ID, "PREMIUM", "TEST", now
        );
        jdbcTemplate.update(
                "insert into support_tickets (store_id, subject, status, created_at, updated_at, last_message_at) values (?, ?, ?, ?, ?, ?)",
                STORE_ID, "Test", "OPEN", now, now, now
        );
        Long ticketId = jdbcTemplate.queryForObject(
                "select id from support_tickets where store_id = ?",
                Long.class,
                STORE_ID
        );
        jdbcTemplate.update(
                "insert into support_messages (ticket_id, author_type, message, created_at) values (?, ?, ?, ?)",
                ticketId, "STORE", "test", now
        );

        service.erase(STORE_ID);
        service.erase(STORE_ID);

        assertThat(count("stores", "store_id", STORE_ID)).isZero();
        assertThat(count("personalization_rules", "store_id", STORE_ID)).isZero();
        assertThat(count("personalization_fields", "rule_id", ruleId)).isZero();
        assertThat(count("integration_logs", "store_id", STORE_ID)).isZero();
        assertThat(count("plan_events", "store_id", STORE_ID)).isZero();
        assertThat(count("support_tickets", "store_id", STORE_ID)).isZero();
        assertThat(count("support_messages", "ticket_id", ticketId)).isZero();
    }

    private long count(String table, String column, Long id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?",
                Long.class,
                id
        );
    }
}
