package br.com.nuvemcustomfields.migration;

import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.dao.DuplicateKeyException;
import static org.assertj.core.api.Assertions.*;

class CreemCatalogMigrationTest {
    @Test void migrationPreventsTwoUnfinishedPublicationsAndReleasesMarketOnCompletion() {
        var dataSource = new JdbcDataSource(); dataSource.setURL("jdbc:h2:mem:creem_migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V51__creem_catalog_publications.sql")).execute(dataSource);
        var jdbc = new JdbcTemplate(dataSource);
        String insert = "insert into creem_catalog_publications(publication_key,reservation_key,payload_json,country_code,plan,environment,operator_name) values (?,?,'{}','MX','PREMIUM','SANDBOX','admin')";
        jdbc.update(insert,"first","market");
        assertThatThrownBy(() -> jdbc.update(insert,"second","market")).isInstanceOf(DuplicateKeyException.class);
        jdbc.update("update creem_catalog_publications set status='LINKED',reservation_key=null,product_id='prod_saved' where publication_key='first'");
        jdbc.update(insert,"second","market");
        assertThat(jdbc.queryForObject("select product_id from creem_catalog_publications where publication_key='first'",String.class)).isEqualTo("prod_saved");
    }
}
