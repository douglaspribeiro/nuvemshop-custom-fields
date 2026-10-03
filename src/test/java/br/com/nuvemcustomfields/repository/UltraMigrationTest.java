package br.com.nuvemcustomfields.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

class UltraMigrationTest {
    @Test void addsDisabledUltraCatalogWithoutChangingExistingPrice() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:ultra-migration;MODE=MySQL", "sa", "");
             var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE support_tickets (id BIGINT, store_id BIGINT)");
            sql.execute("CREATE TABLE payment_catalog_prices (provider VARCHAR(30), environment VARCHAR(20), country_code VARCHAR(2), plan VARCHAR(30), currency VARCHAR(3), amount_value DECIMAL(12,2), tax_mode VARCHAR(20), recurring BOOLEAN, enabled BOOLEAN)");
            sql.execute("CREATE TABLE payment_subscriptions (id BIGINT)");
            sql.execute("CREATE TABLE stores (id BIGINT)");
            sql.execute("CREATE TABLE winback_emails (id VARCHAR(36))");
            sql.execute("INSERT INTO payment_catalog_prices VALUES ('EFI','PRODUCTION','BR','PREMIUM_PLUS','BRL',29.99,'internal',TRUE,TRUE)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V38__add_ultra_plan_and_ticket_plan.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V39__track_subscription_upgrade.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V40__upgrade_adjustments.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V41__feature_requests.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V42__store_erasure_requests.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V43__upgrade_coupon_catalog.sql"));
            try(var result=sql.executeQuery("SELECT COUNT(*) FROM upgrade_coupons")){
                assertThat(result.next()).isTrue();assertThat(result.getInt(1)).isZero();
            }
            try (var result = sql.executeQuery("SELECT amount_value, enabled FROM payment_catalog_prices WHERE plan='PREMIUM_PLUS'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo("29.99");
                assertThat(result.getBoolean(2)).isTrue();
            }
            try (var result = sql.executeQuery("SELECT amount_value, enabled FROM payment_catalog_prices WHERE plan='PREMIUM_ULTRA'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo("59.90");
                assertThat(result.getBoolean(2)).isFalse();
                assertThat(result.next()).isFalse();
            }
            sql.executeQuery("SELECT plan_at_open FROM support_tickets").close();
            sql.executeQuery("SELECT upgrade_plan, upgrade_amount, upgrade_price_id FROM payment_subscriptions").close();
            sql.executeQuery("SELECT upgrade_payment_pending FROM payment_subscriptions").close();
            sql.executeQuery("SELECT due_amount, charge_id, state FROM upgrade_adjustments").close();
            sql.executeQuery("SELECT title, description, discord_sent_at FROM feature_requests").close();
            sql.executeQuery("SELECT erasure_requested_at FROM stores").close();
        }
    }
}
