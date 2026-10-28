package br.com.nuvemcustomfields.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.assertj.core.api.Assertions.*;

class UserJourneyAuditMigrationTest {
    @Test void migrationRequiresStoreAndAutomaticallyDatesEveryInsert() {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:journey_migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V63__user_journey_audit.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        jdbc.update("insert into user_journey_audit(store_id,journey_id,actor,action,method,route,outcome) values (1,'journey','MERCHANT','open','GET','/admin','STARTED')");
        assertThat(jdbc.queryForObject("select occurred_at from user_journey_audit", java.sql.Timestamp.class)).isNotNull();
        assertThatThrownBy(() -> jdbc.update("insert into user_journey_audit(journey_id,actor,action,method,route,outcome) values ('journey','MERCHANT','open','GET','/admin','STARTED')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
