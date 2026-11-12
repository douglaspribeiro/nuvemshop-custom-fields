package br.com.nuvemcustomfields.migration;

import br.com.nuvemcustomfields.dto.FieldResponse;
import br.com.nuvemcustomfields.config.StorefrontCacheEntityListener;
import br.com.nuvemcustomfields.entity.PersonalizationField;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.orm.hibernate5.SpringBeanContainer;

import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Explicit CI gate: mvn -Dskip.frontend=true -Dtest=MysqlMigrationContractIT
 * -Dncf.migration.mysql.url=jdbc:mysql://127.0.0.1:3306/ncf_migration_test_contract
 * -Dncf.migration.mysql.password=... test
 * Uses only randomly named disposable schemas on a loopback test server. Never cleans an existing schema.
 */
class MysqlMigrationContractIT {
    @Test void addsBatchOperationsAndPermanentBlocksFromV80() {
        isolatedDatabase(source -> {
            var jdbc = new JdbcTemplate(source);
            flyway(source,"80").migrate();
            assertThat(flyway(source,null).migrate().migrationsExecuted).isEqualTo(2);
            jdbc.update("insert into store_access_blocks(store_id,blocked_at) values(900082,NOW(6))");
            assertThat(jdbc.queryForObject("select count(*) from store_access_blocks where store_id=900082",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from template_batches",Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from template_product_sources",Long.class)).isZero();
            validateHibernateAndReadField(source,null);
        });
    }
    @Test void delaysPreviouslyPendingRenewalEmailsWhenWebhookBufferIsInstalled() {
        isolatedDatabase(source->{
            var jdbc=new JdbcTemplate(source);flyway(source,"79").migrate();
            jdbc.update("INSERT INTO renewal_failure_emails(store_id,provider,subscription_id,payment_id,state,created_at,next_attempt_at) VALUES(900080,'EFI','sub-80','payment-80','PENDING',NOW(6),NOW(6))");
            jdbc.update("INSERT INTO renewal_failure_emails(store_id,provider,subscription_id,payment_id,state,created_at,next_attempt_at) VALUES(900080,'EFI','sub-80','payment-81','SENT',NOW(6),NOW(6))");
            assertThat(flyway(source,"80").migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,created_at,next_attempt_at) FROM renewal_failure_emails WHERE payment_id='payment-80'",Long.class)).isEqualTo(3600L);
            assertThat(jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,created_at,next_attempt_at) FROM renewal_failure_emails WHERE payment_id='payment-81'",Long.class)).isZero();
            validateHibernateAndReadField(source,null);
        });
    }
    @Test void addsSuggestionResponsesWithoutLosingExistingRequestsAndCascadesErasure() {
        isolatedDatabase(source->{
            var jdbc=new JdbcTemplate(source);flyway(source,"78").migrate();
            jdbc.update("INSERT INTO feature_requests(id,store_id,title,description,plan_at_submission,submission_token,created_at,next_notification_at) VALUES(900079,900079,'Arquivos','Quero arquivos','FREE','00000000-0000-0000-0000-000000090079',NOW(),NOW())");
            assertThat(flyway(source,"79").migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM feature_requests WHERE id=900079",String.class)).isEqualTo("RECEIVED");
            assertThat(jdbc.queryForObject("SELECT revision FROM feature_requests WHERE id=900079",Long.class)).isZero();
            jdbc.update("INSERT INTO feature_request_updates(request_id,status,message,submission_token,email_state) VALUES(900079,'AVAILABLE','Já disponível','00000000-0000-0000-0000-000000090078','PENDING')");
            validateHibernateAndReadField(source,null);
            jdbc.update("DELETE FROM feature_requests WHERE id=900079");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feature_request_updates",Long.class)).isZero();
        });
    }
    @Test void upgradesPopulatedV53ThroughV54AndV55WithoutLosingExistingData() {
        isolatedDatabase(source -> {
            var jdbc = new JdbcTemplate(source);
            var baseline = flyway(source, "53");
            baseline.migrate();
            assertThat(baseline.validateWithResult().validationSuccessful).isTrue();
            jdbc.update("INSERT INTO stores (store_id, access_token, plan) VALUES (900001, 'test-only', 'PREMIUM_ULTRA')");
            jdbc.update("INSERT INTO personalization_rules (store_id, product_id, product_name) VALUES (900001, 123, 'Bolsa de teste')");
            Long rule = jdbc.queryForObject("SELECT id FROM personalization_rules WHERE store_id=900001", Long.class);
            for (String type : List.of("TEXT", "TEXTAREA", "NUMBER", "SELECT", "IMAGE_SELECT")) {
                jdbc.update("INSERT INTO personalization_fields (rule_id,label,field_type,required,max_length,sort_order,options_text,image_options_json) VALUES (?,?,?,true,100,0,?,?)",
                        rule, "Cor " + type, type, "Azul marinho\nVerde oliva",
                        type.equals("IMAGE_SELECT") ? "[{\"id\":\"00000000-0000-0000-0000-000000000001\",\"label\":\"Azul marinho\"}]" : null);
            }
            Long imageField = jdbc.queryForObject("SELECT id FROM personalization_fields WHERE field_type='IMAGE_SELECT'", Long.class);
            jdbc.update("INSERT INTO personalization_images (id,store_id,product_id,field_id,bucket,preview_key,thumbnail_key,state,created_at,updated_at,retry_at) VALUES ('00000000-0000-0000-0000-000000000001',900001,123,?,'test-only','preview.jpg','thumbnail.jpg','READY',NOW(),NOW(),NOW())", imageField);
            var original = snapshot(jdbc);

            var zoom = flyway(source, "54");
            assertThat(zoom.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(snapshot(jdbc)).isEqualTo(original);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM personalization_fields WHERE show_image_zoom=true", Integer.class)).isEqualTo(5);
            jdbc.update("UPDATE personalization_fields SET show_image_zoom=false WHERE id=?", imageField);
            assertThatThrownBy(() -> validateHibernateAndReadField(source, null))
                    .hasStackTraceContaining("Schema-validation:");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='personalization_fields' AND column_name='mobile_image_columns'", Integer.class)).isZero();

            var columns = flyway(source, "55");
            assertThat(columns.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(snapshot(jdbc)).isEqualTo(original);
            assertThat(jdbc.queryForObject("SELECT show_image_zoom FROM personalization_fields WHERE id=?", Boolean.class, imageField)).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM personalization_fields WHERE mobile_image_columns IS NULL", Integer.class)).isEqualTo(5);
            // Check V55 independently; later catalog/report migrations are also expected.
            var latest = flyway(source, null);
            int pending = latest.info().pending().length;
            assertThat(latest.migrate().migrationsExecuted).isEqualTo(pending);
            assertThat(latest.validateWithResult().validationSuccessful).isTrue();
            var upgraded = snapshot(jdbc);
            // Later migrations may add rows, tables and columns. Every original column value must survive.
            original.forEach((table, rows) -> rows.forEach(row ->
                    assertThat(upgraded.get(table)).as("Preserved V53 data in %s: %s", table, row)
                            .anySatisfy(actual -> assertThat(actual).containsAllEntriesOf(row))));
            assertThat(latest.migrate().migrationsExecuted).isZero();
            assertThat(snapshot(jdbc)).isEqualTo(upgraded);

            // Old writers omit both new columns; defaults must work in actual MySQL as well as H2.
            jdbc.update("INSERT INTO personalization_fields (rule_id,label,field_type) VALUES (?,'Campo legado','TEXT')", rule);
            assertThat(jdbc.queryForObject("SELECT show_image_zoom FROM personalization_fields WHERE label='Campo legado'", Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("SELECT mobile_image_columns FROM personalization_fields WHERE label='Campo legado'", Integer.class)).isNull();
            jdbc.update("UPDATE personalization_fields SET mobile_image_columns=3 WHERE id=?", imageField);
            validateHibernateAndReadField(source, imageField);
        });
    }

    @Test void upgradesV72ToPlanAccessWithoutChangingBuyerFilesOrSessions() {
        isolatedDatabase(source -> {
            var jdbc=new JdbcTemplate(source);
            flyway(source,"72").migrate();
            jdbc.update("INSERT INTO stores(store_id,access_token,plan) VALUES(900003,'test-only','PREMIUM_ULTRA')");
            jdbc.update("INSERT INTO buyer_upload_entitlements(store_id,offer_code,quota_bytes,enabled,created_at) VALUES(900003,'BUYER_UPLOAD_V1',25000000000,true,NOW())");
            jdbc.update("INSERT INTO buyer_uploads(id,store_id,product_id,field_id,session_hash,request_id,size_bytes,state,order_id,order_item_id,created_at,expires_at,bucket,object_key) VALUES('file-test',900003,123,456,'session-test','request-test',100,'ORDERED',789,987,NOW(),DATE_ADD(NOW(),INTERVAL 90 DAY),'private-test','test/art.png')");
            jdbc.update("INSERT INTO buyer_upload_sessions(session_hash,store_id,created_at,expires_at) VALUES('session-test',900003,NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY))");
            var files=jdbc.queryForList("SELECT * FROM buyer_uploads");
            var sessions=jdbc.queryForList("SELECT * FROM buyer_upload_sessions");
            assertThat(flyway(source,"73").migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT * FROM buyer_uploads")).isEqualTo(files);
            assertThat(jdbc.queryForList("SELECT * FROM buyer_upload_sessions")).isEqualTo(sessions);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='buyer_upload_entitlements'",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='payment_subscriptions' AND column_name='buyer_upload_offer'",Integer.class)).isZero();
        });
    }

    @Test void multipleFilesMigrationDefaultsOldFieldsToOneAndCleanupKeepsWarnings() {
        isolatedDatabase(source -> {
            var jdbc=new JdbcTemplate(source);flyway(source,"75").migrate();
            jdbc.update("INSERT INTO personalization_rules(id,store_id,product_id) VALUES(900077,900077,123)");
            jdbc.update("INSERT INTO personalization_fields(id,rule_id,label,field_type) VALUES(900077,900077,'Arte','FILE_UPLOAD')");
            jdbc.update("INSERT INTO integration_logs(store_id,level,event_type,message) VALUES(900077,'INFO','storefront.sdk.rendered','routine'),(900077,'WARN','storefront.sdk.rendered','warning'),(900077,'ERROR','other.error','error')");
            var latest=flyway(source,null);latest.migrate();
            assertThat(jdbc.queryForObject("SELECT max_files FROM personalization_fields WHERE id=900077",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM integration_logs WHERE store_id=900077 AND level='INFO'",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM integration_logs WHERE store_id=900077 AND level IN ('WARN','ERROR')",Integer.class)).isEqualTo(2);
            assertThat(latest.migrate().migrationsExecuted).isZero();validateHibernateAndReadField(source,null);
        });
    }

    @Test void upgradesProductionV70AndKeepsBothManualOverrideAndHistoricalUploadLocation() {
        isolatedDatabase(source -> {
            var jdbc=new JdbcTemplate(source);
            var production=flyway(source,"70");production.migrate();
            jdbc.update("INSERT INTO stores(store_id,access_token,plan,manual_plan_override) VALUES(900004,'test-only','PREMIUM_PLUS',true)");
            assertThat(flyway(source,"74").migrate().migrationsExecuted).isEqualTo(4);
            jdbc.update("INSERT INTO buyer_uploads(id,store_id,product_id,field_id,session_hash,request_id,size_bytes,state,created_at,expires_at,bucket,object_key) VALUES('legacy-aws',900004,123,456,'session','request',100,'TEMPORARY',NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),'original-bucket','original/key')");
            var latest=flyway(source,null);
            int pending=latest.info().pending().length;
            assertThat(latest.migrate().migrationsExecuted).isEqualTo(pending);
            assertThat(latest.validateWithResult().validationSuccessful).isTrue();
            assertThat(jdbc.queryForObject("SELECT manual_plan_override FROM stores WHERE store_id=900004",Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("SELECT buyer_upload_storage_provider FROM stores WHERE store_id=900004",String.class)).isNull();
            assertThat(jdbc.queryForObject("SELECT storage_provider FROM buyer_uploads WHERE id='legacy-aws'",String.class)).isEqualTo("AWS_S3");
            assertThat(jdbc.queryForObject("SELECT bucket FROM buyer_uploads WHERE id='legacy-aws'",String.class)).isEqualTo("original-bucket");
            assertThat(jdbc.queryForObject("SELECT object_key FROM buyer_uploads WHERE id='legacy-aws'",String.class)).isEqualTo("original/key");
            assertThat(latest.migrate().migrationsExecuted).isZero();
        });
    }

    @Test void preservesHistoricalFieldNamesAndQueuesReclassificationOnMysql() {
        isolatedDatabase(source -> {
            var jdbc=new JdbcTemplate(source);
            flyway(source,"58").migrate();
            jdbc.update("INSERT INTO stores(store_id,access_token,plan) VALUES(900002,'test-only','PREMIUM')");
            jdbc.update("INSERT INTO personalization_rules(store_id,product_id,product_name) VALUES(900002,123,'Teste')");
            Long rule=jdbc.queryForObject("SELECT id FROM personalization_rules WHERE store_id=900002",Long.class);
            for (String name:List.of("Nome [gravar]","nome [gravar]"))
                jdbc.update("INSERT INTO personalization_fields(rule_id,label,field_type) VALUES(?,?,'TEXT')",rule,name);
            jdbc.update("INSERT INTO store_sales_sync(store_id,complete,personalized_value_backfilled,last_synced_at) VALUES(900002,true,true,'2026-10-01 12:00:00')");
            flyway(source,"59").migrate();
            assertThat(jdbc.queryForList("SELECT property_name FROM personalization_field_history WHERE store_id=900002",String.class))
                    .containsExactlyInAnyOrder("Nome (gravar)","nome (gravar)");
            assertThat(jdbc.queryForObject("SELECT complete FROM store_sales_sync WHERE store_id=900002",Boolean.class)).isFalse();
            assertThat(jdbc.queryForObject("SELECT last_synced_at FROM store_sales_sync WHERE store_id=900002",java.sql.Timestamp.class))
                    .isEqualTo(java.sql.Timestamp.valueOf("2026-10-01 12:00:00"));
            flyway(source,"60").migrate();
            assertThat(jdbc.queryForObject("SELECT historical_through FROM store_sales_sync WHERE store_id=900002",java.sql.Timestamp.class)).isNull();
            jdbc.update("DELETE FROM personalization_fields WHERE rule_id=?",rule);
            jdbc.update("DELETE FROM personalization_rules WHERE id=?",rule);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM personalization_field_history WHERE store_id=900002",Long.class)).isEqualTo(2);
            jdbc.update("DELETE FROM stores WHERE store_id=900002");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM personalization_field_history WHERE store_id=900002",Long.class)).isZero();
        });
    }

    @Test void freshInstallationMigratesAndValidatesAllEntityMappings() {
        isolatedDatabase(source -> {
            var migrations = flyway(source, null);
            assertThat(migrations.migrate().migrationsExecuted).isPositive();
            assertThat(migrations.validateWithResult().validationSuccessful).isTrue();
            validateHibernateAndReadField(source, null);
            assertThat(migrations.migrate().migrationsExecuted).isZero();
        });
    }

    private Flyway flyway(DriverManagerDataSource source, String target) {
        var config = Flyway.configure().dataSource(source).locations("classpath:db/migration").cleanDisabled(true);
        if (target != null) config.target(target);
        return config.load();
    }

    private void validateHibernateAndReadField(DriverManagerDataSource source, Long imageField) {
        // Old migration fixtures also validate the entity mappings of the current release.
        flyway(source, null).migrate();
        var context = new GenericApplicationContext();
        context.registerBean(StorefrontCacheEntityListener.class);
        context.refresh();
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setPackagesToScan("br.com.nuvemcustomfields.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
                "hibernate.resource.beans.container", new SpringBeanContainer(context.getBeanFactory()),
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        try {
            factory.afterPropertiesSet();
            if (imageField != null) {
                try (var em = factory.getObject().createEntityManager()) {
                    var field = em.find(PersonalizationField.class, imageField);
                    var response = FieldResponse.from(field);
                    assertThat(response.showImageZoom()).isFalse();
                    assertThat(response.mobileImageColumns()).isEqualTo(3);
                    assertThat(response.options()).containsExactly("Azul marinho");
                }
            }
        } finally {
            factory.destroy();
            context.close();
        }
    }

    /** Keep column values available for subset checks after additive schema migrations. */
    private Map<String, List<Map<String, Object>>> snapshot(JdbcTemplate jdbc) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history' ORDER BY table_name", String.class)) {
            result.put(table, jdbc.queryForList("SELECT * FROM `" + table.replace("`", "``") + "`").stream().map(row -> {
                Map<String, Object> values = new LinkedHashMap<>(row);
                if (table.equals("personalization_fields")) {
                    values.remove("show_image_zoom");
                    values.remove("mobile_image_columns");
                }
                return values;
            }).sorted(Comparator.comparing(Map::toString)).toList());
        }
        return result;
    }

    private void isolatedDatabase(Consumer<DriverManagerDataSource> test) {
        String url = System.getProperty("ncf.migration.mysql.url");
        assertThat(url).as("Provide the isolated MySQL URL; this gate must never silently skip").isNotBlank();
        URI address = URI.create(url.substring("jdbc:".length()));
        assertThat(address.getScheme()).isEqualTo("mysql");
        assertThat(address.getHost()).as("Only a local disposable MySQL is allowed").isEqualTo("127.0.0.1");
        assertThat(address.getPath()).isEqualTo("/ncf_migration_test_contract");
        var admin = new DriverManagerDataSource(url, "root", System.getProperty("ncf.migration.mysql.password"));
        var jdbc = new JdbcTemplate(admin);
        String schema = "ncf_migration_test_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        try {
            test.accept(new DriverManagerDataSource(url.replace("/ncf_migration_test_contract", "/" + schema), "root", System.getProperty("ncf.migration.mysql.password")));
        } finally {
            jdbc.execute("DROP DATABASE `" + schema + "`");
        }
    }
}
