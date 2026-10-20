package br.com.nuvemcustomfields.migration;

import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.dao.DuplicateKeyException;
import static org.assertj.core.api.Assertions.*;

class StripePublicationMigrationTest {
    @Test void unfinishedPublicationReservesThePlanAndCompletionPreservesRemoteIds() {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:stripe_publications;MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V57__stripe_catalog_publications.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        String insert="insert into stripe_catalog_publications(publication_key,reservation_key,payload_json,country_code,plan,environment,operator_name) values (?,?,'{}','AR','PREMIUM','SANDBOX','admin')";
        jdbc.update(insert,"first","market");
        assertThatThrownBy(() -> jdbc.update(insert,"second","market")).isInstanceOf(DuplicateKeyException.class);
        jdbc.update("update stripe_catalog_publications set status='LINKED',reservation_key=null,product_id='prod_1',price_id='price_1' where publication_key='first'");
        jdbc.update(insert,"second","market");
        assertThat(jdbc.queryForObject("select price_id from stripe_catalog_publications where publication_key='first'",String.class)).isEqualTo("price_1");
        assertThat(jdbc.queryForObject("select product_id from stripe_catalog_publications where publication_key='first'",String.class)).isEqualTo("prod_1");
    }
}
