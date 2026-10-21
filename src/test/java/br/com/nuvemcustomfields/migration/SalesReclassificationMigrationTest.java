package br.com.nuvemcustomfields.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class SalesReclassificationMigrationTest {
    @Test void queuesHistoricalReimportWithoutDeletingStoredSales() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:sales_reclassification;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE store_sales_sync(store_id BIGINT PRIMARY KEY, complete BOOLEAN, personalized_value_backfilled BOOLEAN, last_attempt_at TIMESTAMP, last_error VARCHAR(500))");
        jdbc.execute("CREATE TABLE store_order_sales(store_id BIGINT, order_id BIGINT)");
        jdbc.execute("INSERT INTO store_sales_sync VALUES(1,TRUE,TRUE,CURRENT_TIMESTAMP,'old error')");
        jdbc.execute("INSERT INTO store_order_sales VALUES(1,99)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V58__reclassify_sales_using_configured_fields.sql")).execute(source);
        var state=jdbc.queryForMap("SELECT * FROM store_sales_sync WHERE store_id=1");
        assertThat(state.get("COMPLETE")).isEqualTo(false);
        assertThat(state.get("PERSONALIZED_VALUE_BACKFILLED")).isEqualTo(false);
        assertThat(state.get("LAST_ATTEMPT_AT")).isNull();
        assertThat(state.get("LAST_ERROR")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM store_order_sales", Long.class)).isEqualTo(1);
    }
}
