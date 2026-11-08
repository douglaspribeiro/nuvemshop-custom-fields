package br.com.nuvemcustomfields.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

class ManualPlanOverrideMigrationTest {
    @Test void protectsOnlyTheLatestMatchingPaidOverride() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:manual_override;MODE=MySQL;DB_CLOSE_DELAY=-1");
        var db = new JdbcTemplate(ds);
        db.execute("create table stores(store_id bigint primary key, plan varchar(30), billing_suspended boolean)");
        db.execute("create table plan_events(id bigint primary key, store_id bigint, source varchar(30), to_plan varchar(30))");
        db.execute("create table payment_subscriptions(store_id bigint primary key, access_active boolean)");
        db.update("insert into stores values (6177123,'PREMIUM_PLUS',false),(2,'PREMIUM',false),(3,'FREE',false),(4,'PREMIUM_PLUS',false)");
        db.update("insert into plan_events values (1,6177123,'OVERRIDE','PREMIUM_PLUS'),(2,6177123,'PAYMENT_RECONCILE','FREE'),(3,6177123,'OVERRIDE','PREMIUM_PLUS'),(4,2,'OVERRIDE','PREMIUM'),(5,2,'PAYMENT_RECONCILE','PREMIUM'),(6,3,'OVERRIDE','FREE'),(7,4,'OVERRIDE','PREMIUM')");
        db.update("insert into stores values (5,'PREMIUM_PLUS',false)");
        db.update("insert into plan_events values (8,5,'OVERRIDE','PREMIUM_PLUS')");
        db.update("insert into payment_subscriptions values (6177123,false),(5,true)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V70__manual_plan_override.sql")).execute(ds);
        assertThat(db.queryForList("select store_id from stores where manual_plan_override=true", Long.class)).containsExactly(6177123L);
        assertThat(db.queryForObject("select plan from stores where store_id=6177123", String.class)).isEqualTo("PREMIUM_PLUS");
    }
}
