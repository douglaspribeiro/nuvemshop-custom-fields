package br.com.nuvemcustomfields.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class RenewalFailureMigrationTest {
    @Test void preservesOldSubscriptionsAndDeduplicatesFailureEmails() {
        var ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:renewal_migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
        var db=new JdbcTemplate(ds); db.execute("create table payment_subscriptions(id bigint primary key)");
        db.update("insert into payment_subscriptions values(1)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V65__renewal_failure_grace_and_email.sql")).execute(ds);
        assertThat(db.queryForObject("select count(*) from payment_subscriptions where renewal_failure_detected_at is null",Integer.class)).isEqualTo(1);
        String sql="insert into renewal_failure_emails(store_id,provider,subscription_id,payment_id) values (12,'EFI','contract','charge')";
        db.update(sql);
        assertThatThrownBy(() -> db.update(sql)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(db.queryForObject("select state from renewal_failure_emails",String.class)).isEqualTo("PENDING");
    }
}
