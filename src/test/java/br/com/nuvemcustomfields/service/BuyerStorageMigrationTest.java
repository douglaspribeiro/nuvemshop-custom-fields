package br.com.nuvemcustomfields.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class BuyerStorageMigrationTest {
    @Test void oldObjectsRemainAwsAndExistingStoresFollowTheGlobalDefault() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:storage-migration;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("create table buyer_uploads(id varchar(36) primary key, bucket varchar(120), object_key varchar(255))");
        jdbc.execute("create table stores(store_id bigint primary key)");
        jdbc.update("insert into buyer_uploads values ('old','old-bucket','old-key')");
        jdbc.update("insert into stores values (1)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V74__buyer_upload_storage_provider.sql")).execute(ds);
        assertThat(jdbc.queryForMap("select * from buyer_uploads where id='old'"))
                .containsEntry("STORAGE_PROVIDER","AWS_S3").containsEntry("BUCKET","old-bucket").containsEntry("OBJECT_KEY","old-key");
        assertThat(jdbc.queryForObject("select buyer_upload_storage_provider from stores",String.class)).isNull();
    }
}
