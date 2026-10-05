package br.com.nuvemcustomfields.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Ga4MigrationTest {
    @Test void migrationCreatesContextColumnsAndEnforcesUniqueOutboxEvents() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:ga4migration;MODE=MySQL")){
            var sql=connection.createStatement();
            sql.execute("CREATE TABLE payment_subscriptions (id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE upgrade_adjustments (id VARCHAR(36) PRIMARY KEY)");
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V46__ga4_billing_events.sql"));
            sql.execute("INSERT INTO payment_subscriptions (id,analytics_client_id,analytics_session_id,analytics_first_payment_id) VALUES (1,'123.456','789','charge')");
            sql.execute("INSERT INTO upgrade_adjustments (id,analytics_client_id,analytics_session_id) VALUES ('upgrade','123.456','789')");
            String insert="INSERT INTO ga4_outbox (event_key,payload,created_at,next_attempt_at) VALUES ('key','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)";
            sql.execute(insert);
            assertThatThrownBy(()->sql.execute(insert)).isInstanceOf(java.sql.SQLException.class);
            try(var rows=sql.executeQuery("SELECT COUNT(*) FROM ga4_outbox")){
                rows.next();assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
    }
}
