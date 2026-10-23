package br.com.nuvemcustomfields.migration;

import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.assertj.core.api.Assertions.*;

class DlocalGoCatalogMigrationTest {
    @Test void seedsSixDisabledPricesWithoutChangingTheExistingMarketRoute() {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:dlocal_go_migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table payment_catalog_prices (provider varchar(30),environment varchar(20),country_code varchar(2),plan varchar(30),currency varchar(3),amount_value decimal(12,2),provider_price_id varchar(120),tax_mode varchar(20),recurring boolean,enabled boolean)");
        jdbc.execute("create table payment_routing_rules(country_code varchar(2),provider varchar(30),enabled boolean)");
        jdbc.execute("insert into payment_routing_rules values ('AR','CREEM',true)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V61__dlocal_go_argentina_catalog.sql")).execute(source);
        assertThat(jdbc.queryForObject("select count(*) from payment_catalog_prices where provider='DLOCAL_GO' and country_code='AR' and enabled=false and provider_price_id is null and recurring=true",Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select provider from payment_routing_rules where country_code='AR'",String.class)).isEqualTo("CREEM");
    }
    @Test void operationsAndReversalLedgerHaveIndependentTables() {
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:dlocal_operations;MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V62__dlocal_go_operations.sql")).execute(source);
        var jdbc=new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("select count(*) from dlocal_go_operations",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from dlocal_go_reversals",Integer.class)).isZero();
    }

}
