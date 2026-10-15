package br.com.nuvemcustomfields.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class DodoTestCatalogMigrationTest {
    JdbcDataSource database() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:dodo_seed_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        new JdbcTemplate(source).execute("""
            create table payment_catalog_prices (
                id bigint auto_increment primary key,
                provider varchar(30) not null, environment varchar(20) not null,
                country_code varchar(2) not null, plan varchar(30) not null,
                currency varchar(3) not null, amount_value decimal(12,2) not null,
                provider_price_id varchar(120), tax_mode varchar(20) not null,
                recurring boolean not null, enabled boolean not null,
                validation_error varchar(500), validated_at timestamp,
                unique(provider,environment,country_code,plan)
            )
            """);
        return source;
    }
    void migrate(JdbcDataSource source) {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V53__seed_dodo_test_catalog.sql")).execute(source);
    }
    @Test void seedsTheVerifiedMonthlyCatalogForFourCountriesWithoutEnablingSales() {
        var source=database(); var jdbc=new JdbcTemplate(source); migrate(source);
        assertThat(jdbc.queryForObject("select count(*) from payment_catalog_prices",Long.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from payment_catalog_prices where provider <> 'DODO_TEST' or environment <> 'SANDBOX' or enabled = true or recurring = false or validated_at is null or validation_error is not null",Long.class)).isZero();
        var ids=Map.of("PREMIUM","pdt_0NpncfPHBuCeAoWYEMKWi","PREMIUM_PLUS","pdt_0NpncfTZsACz359krtMyz","PREMIUM_ULTRA","pdt_0NpncfWWeuij8hUmcu1vb");
        var usd=Map.of("PREMIUM","4.99","PREMIUM_PLUS","7.49","PREMIUM_ULTRA","14.99");
        var clp=Map.of("PREMIUM","4199","PREMIUM_PLUS","6299","PREMIUM_ULTRA","12599");
        var mxn=Map.of("PREMIUM","99","PREMIUM_PLUS","149","PREMIUM_ULTRA","299");
        for(String plan:ids.keySet()) for(String country:new String[]{"AR","CO","CL","MX"}) {
            var row=jdbc.queryForMap("select * from payment_catalog_prices where plan=? and country_code=?",plan,country);
            var lower=new java.util.HashMap<String,Object>(); row.forEach((k,v)->lower.put(k.toLowerCase(java.util.Locale.ROOT),v));
            String currency=country.equals("CL")?"CLP":country.equals("MX")?"MXN":"USD";
            String amount=(country.equals("CL")?clp:country.equals("MX")?mxn:usd).get(plan);
            assertThat(lower.get("provider_price_id")).isEqualTo(ids.get(plan));
            assertThat(lower.get("currency")).isEqualTo(currency);
            assertThat((BigDecimal)lower.get("amount_value")).isEqualByComparingTo(amount);
        }
    }
    @Test void preservesExistingBindingsLivePricesAndOtherPaymentTablesOnRepeatedExecution() {
        var source=database();var jdbc=new JdbcTemplate(source);
        jdbc.update("insert into payment_catalog_prices(provider,environment,country_code,plan,currency,amount_value,provider_price_id,tax_mode,recurring,enabled,validated_at) values ('DODO_TEST','SANDBOX','CL','PREMIUM','CLP',5000,'pdt_custom','external',true,true,'2026-10-01 00:00:00')");
        jdbc.update("insert into payment_catalog_prices(provider,environment,country_code,plan,currency,amount_value,provider_price_id,tax_mode,recurring,enabled) values ('DODO','PRODUCTION','MX','PREMIUM','MXN',123,'pdt_live','external',true,true)");
        jdbc.execute("create table payment_routing_rules(country_code varchar(2) primary key,provider varchar(30))");
        jdbc.execute("create table payment_subscriptions(store_id bigint primary key,provider varchar(30))");
        jdbc.update("insert into payment_routing_rules values ('BR','EFI'),('CL','CREEM')");
        jdbc.update("insert into payment_subscriptions values (42,'EFI')");
        migrate(source);migrate(source);
        assertThat(jdbc.queryForObject("select count(*) from payment_catalog_prices",Long.class)).isEqualTo(13);
        assertThat(jdbc.queryForObject("select provider_price_id from payment_catalog_prices where provider='DODO_TEST' and country_code='CL' and plan='PREMIUM'",String.class)).isEqualTo("pdt_custom");
        assertThat(jdbc.queryForObject("select enabled from payment_catalog_prices where provider='DODO_TEST' and country_code='CL' and plan='PREMIUM'",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select amount_value from payment_catalog_prices where provider='DODO'",BigDecimal.class)).isEqualByComparingTo("123");
        assertThat(jdbc.queryForObject("select provider from payment_routing_rules where country_code='CL'",String.class)).isEqualTo("CREEM");
        assertThat(jdbc.queryForObject("select provider from payment_subscriptions where store_id=42",String.class)).isEqualTo("EFI");
    }
}
