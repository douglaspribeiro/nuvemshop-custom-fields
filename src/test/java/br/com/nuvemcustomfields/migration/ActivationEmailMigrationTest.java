package br.com.nuvemcustomfields.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class ActivationEmailMigrationTest {
    @Test void migrationKeepsHistoryAndDeduplicatesEachInstallationStep() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:nc39_migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V67__nc39_activation_email_outbox.sql")).execute(ds);
        var db=new JdbcTemplate(ds);
        String sql="insert into nc39_activation_email_outbox(store_id,installed_at,step,next_attempt_at,template_version) values(12,'2026-11-01',?,'2026-11-04','nc39-v1')";
        db.update(sql,"D3");assertThatThrownBy(()->db.update(sql,"D3")).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        db.update(sql,"D7");db.update(sql,"D11");db.update(sql,"D15");
        db.update("update nc39_activation_email_outbox set state='CLAIMED' where step='D3'");
        db.update("update nc39_activation_email_outbox set state='REVIEW' where step='D7'");
        db.update("update nc39_activation_email_outbox set state='SUPPRESSED',suppression_reason='d15_temporarily_disabled' where step='D15'");
        assertThat(db.queryForObject("select count(*) from nc39_activation_email_outbox",Integer.class)).isEqualTo(4);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V68__nc39_email_protections.sql")).execute(ds);
        db.update("update nc39_activation_email_outbox set unsubscribe_token_hash='hash',recipient_hash='recipient',ses_message_id='provider' where step='D3'");
        assertThatThrownBy(() -> db.update("update nc39_activation_email_outbox set unsubscribe_token_hash='hash' where step='D7'"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        db.update("insert into nc39_activation_email_suppressions(store_id,reason,suppressed_at) values(12,'ses_bounce',CURRENT_TIMESTAMP)");
        assertThatThrownBy(() -> db.update("insert into nc39_activation_email_suppressions(store_id,reason,suppressed_at) values(12,'unsubscribe',CURRENT_TIMESTAMP)"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        db.update("insert into nc39_activation_email_feedback(id,store_id,event_id,event_type,occurred_at) values('receipt',12,1,'Bounce',CURRENT_TIMESTAMP)");
        assertThatThrownBy(() -> db.update("insert into nc39_activation_email_feedback(id,store_id,event_id,event_type,occurred_at) values('receipt',12,1,'Bounce',CURRENT_TIMESTAMP)"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(db.queryForObject("select state from nc39_activation_email_outbox where step='D11'",String.class)).isEqualTo("PENDING");
    }
}
