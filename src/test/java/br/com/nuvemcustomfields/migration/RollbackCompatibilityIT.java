package br.com.nuvemcustomfields.migration;

import br.com.nuvemcustomfields.entity.FieldType;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.URI;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.flyway.enabled=true","spring.flyway.target=current","spring.jpa.hibernate.ddl-auto=validate","app.background.enabled=false",
        "activation.email.enabled=false","winback.enabled=false"})
class RollbackCompatibilityIT {
    static DriverManagerDataSource source;
    static JdbcTemplate admin;
    static String schema;
    static long history;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired TestRestTemplate http;
    @Autowired PersonalizationFieldRepository fields;
    @Autowired StoreRepository stores;
    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties) {
        String url=System.getProperty("ncf.migration.mysql.url");
        assertThat(url).isNotBlank();var uri=URI.create(url.substring(5));
        assertThat(uri.getHost()).isEqualTo("127.0.0.1");
        String password=System.getProperty("ncf.migration.mysql.password");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,"root",password));
        schema="ncf_rollback_test_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        String applicationUrl=url.replace("/ncf_migration_test_contract","/"+schema);
        source=new DriverManagerDataSource(applicationUrl,"root",password);
        var migration=Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        migration.migrate();
        var db=new JdbcTemplate(source);
        history=db.queryForObject("select count(*) from flyway_schema_history",Long.class);
        db.update("insert into stores(store_id,access_token,plan,manual_plan_override,buyer_upload_storage_provider) values(991001,'test-only','PREMIUM_PLUS',true,'OCI_OBJECT_STORAGE')");
        db.update("insert into personalization_rules(store_id,product_id,product_name) values(991001,123,'Caneca')");
        Long rule=db.queryForObject("select id from personalization_rules where store_id=991001",Long.class);
        db.update("insert into personalization_fields(rule_id,label,field_type,required) values(?,'Arte','FILE_UPLOAD',true)",rule);
        db.update("insert into buyer_uploads(id,store_id,product_id,field_id,session_hash,request_id,size_bytes,state,created_at,expires_at,bucket,object_key,storage_provider) values('saved-file',991001,123,1,'session','request',100,'ORDERED',NOW(),DATE_ADD(NOW(),INTERVAL 90 DAY),'original-bucket','original/key','AWS_S3')");
        db.update("insert into store_templates(store_id,name,origin,fields_json,revision,created_at,updated_at) values(991001,'Preservar','CUSTOM','[]',1,NOW(),NOW())");
        properties.add("spring.datasource.url",()->applicationUrl);
        properties.add("spring.datasource.username",()->"root");
        properties.add("spring.datasource.password",()->password);
    }
    @Test void rollbackStartsWithV75AndReadsNewFieldTypesWithoutChangingHistoryOrFiles() {
        assertThat(http.getForEntity("/actuator/health",String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(flyway.getConfiguration().getTarget()).isEqualTo(org.flywaydb.core.api.MigrationVersion.fromVersion("current"));
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history",Long.class)).isEqualTo(history);
        var savedFields=fields.findWithRuleByStoreId(991001L);
        assertThat(savedFields).extracting(f->f.getFieldType()).containsExactly(FieldType.FILE_UPLOAD);
        var response=br.com.nuvemcustomfields.dto.FieldResponse.from(savedFields.getFirst());
        assertThat(response.required()).isTrue();
        assertThat(java.util.regex.Pattern.compile(response.validationPattern()).matcher("texto não é arquivo").matches()).isFalse();
        assertThat(jdbc.queryForObject("select object_key from buyer_uploads where id='saved-file'",String.class)).isEqualTo("original/key");
        assertThat(jdbc.queryForObject("select storage_provider from buyer_uploads where id='saved-file'",String.class)).isEqualTo("AWS_S3");
        assertThat(jdbc.queryForObject("select count(*) from store_templates where store_id=991001",Long.class)).isEqualTo(1);
        var store=stores.findByStoreId(991001L).orElseThrow();store.setStoreName("Nome atualizado durante rollback");stores.saveAndFlush(store);
        assertThat(jdbc.queryForObject("select buyer_upload_storage_provider from stores where store_id=991001",String.class)).isEqualTo("OCI_OBJECT_STORAGE");
        assertThat(Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate().migrationsExecuted).isZero();
    }
    @Test void rollbackDoesNotUpgradeAnExistingV70Database() {
        String other=schema+"_v70";
        admin.execute("CREATE DATABASE `"+other+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        var old=new DriverManagerDataSource(source.getUrl().replace(schema,other),"root",source.getPassword());
        try {
            Flyway.configure().dataSource(old).locations("classpath:db/migration").target("70").load().migrate();
            var rollback=Flyway.configure().dataSource(old).locations("classpath:db/migration").target("current").load();
            assertThat(rollback.migrate().migrationsExecuted).isZero();
            assertThat(rollback.info().current().getVersion().toString()).isEqualTo("70");
        } finally {admin.execute("DROP DATABASE `"+other+"`");}
    }
    @AfterAll static void cleanup() {if(admin!=null&&schema!=null)admin.execute("DROP DATABASE `"+schema+"`");}
}
