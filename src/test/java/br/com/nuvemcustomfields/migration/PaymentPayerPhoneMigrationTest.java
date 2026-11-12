package br.com.nuvemcustomfields.migration;

import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentPayerPhoneMigrationTest {
    @Test void addsNullablePhoneWithoutChangingExistingSubscription() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:payer_phone_migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table payment_subscriptions (id bigint primary key, payer_email varchar(254))");
        jdbc.update("insert into payment_subscriptions(id,payer_email) values (1,'payer@example.com')");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V74__payment_payer_phone.sql")).execute(source);
        assertThat(jdbc.queryForObject("select payer_email from payment_subscriptions where id=1",String.class))
                .isEqualTo("payer@example.com");
        assertThat(jdbc.queryForObject("select payer_phone from payment_subscriptions where id=1",String.class)).isNull();
        jdbc.update("update payment_subscriptions set payer_phone=? where id=1", "+5511999999999");
        assertThat(jdbc.queryForObject("select payer_phone from payment_subscriptions where id=1",String.class))
                .isEqualTo("+5511999999999");
    }
}
