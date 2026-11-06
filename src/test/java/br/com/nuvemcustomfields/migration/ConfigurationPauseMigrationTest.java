package br.com.nuvemcustomfields.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class ConfigurationPauseMigrationTest {
    @Test void preservesExistingRulesAndManualStateAndCreatesDurableNoticeQueue() {
        var ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:nc04_migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
        var db=new JdbcTemplate(ds);
        db.execute("create table personalization_rules(id bigint primary key, enabled boolean not null)");
        db.update("insert into personalization_rules values(1,true),(2,false)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V66__configuration_plan_pause.sql")).execute(ds);
        assertThat(db.queryForObject("select count(*) from personalization_rules where plan_paused=false",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("select enabled from personalization_rules where id=2",Boolean.class)).isFalse();
        db.update("insert into configuration_pause_emails(store_id,paused_products) values(12,'100,200')");
        assertThat(db.queryForObject("select state from configuration_pause_emails",String.class)).isEqualTo("PENDING");
    }
}
