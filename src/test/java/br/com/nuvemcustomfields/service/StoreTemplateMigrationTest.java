package br.com.nuvemcustomfields.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class StoreTemplateMigrationTest {
    @Test void addsTemplateSchemaAndBackfillsOnlyExistingReadyFieldReferences() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:templateMigration;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var jdbc=new JdbcTemplate(source);
        jdbc.execute("create table personalization_images(id varchar(36),store_id bigint,product_id bigint,field_id bigint,state varchar(20))");
        jdbc.update("insert into personalization_images values ('bound',1,123,11,'READY'),('unbound',1,123,null,'READY'),('pending',1,123,12,'PENDING')");
        try(var connection=source.getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V65__reusable_store_templates.sql"));}
        assertEquals(1,jdbc.queryForObject("select count(*) from personalization_image_references",Integer.class));
        assertEquals("bound",jdbc.queryForObject("select image_id from personalization_image_references",String.class));
        assertEquals(3,jdbc.queryForObject("select count(*) from personalization_images",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from store_templates",Integer.class));
    }
}
