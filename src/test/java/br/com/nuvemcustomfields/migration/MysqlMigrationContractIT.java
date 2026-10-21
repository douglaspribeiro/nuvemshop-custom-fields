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
                    .hasStackTraceContaining("missing column [mobile_image_columns]");

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
            // Later migrations may add catalog rows and tables, but must preserve the seeded business data.
            original.forEach((table, rows) -> assertThat(upgraded.get(table)).containsAll(rows));
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

    /** Compare all business rows, including option JSON and image ownership, excluding only the two new columns. */
    private Map<String, List<String>> snapshot(JdbcTemplate jdbc) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String table : jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history' ORDER BY table_name", String.class)) {
            result.put(table, jdbc.queryForList("SELECT * FROM `" + table.replace("`", "``") + "`").stream().map(row -> {
                var values = new LinkedHashMap<>(row);
                if (table.equals("personalization_fields")) {
                    values.remove("show_image_zoom");
                    values.remove("mobile_image_columns");
                }
                return values.toString();
            }).sorted().toList());
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
