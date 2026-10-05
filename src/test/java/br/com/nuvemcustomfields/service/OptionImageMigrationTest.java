package br.com.nuvemcustomfields.service;

import org.junit.jupiter.api.Test;
import org.h2.tools.RunScript;
import java.sql.DriverManager;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class OptionImageMigrationTest {
    @Test void migrationCreatesLedgerThatSurvivesOwnerRemoval() throws Exception {
        try (var connection=DriverManager.getConnection("jdbc:h2:mem:image-migration;MODE=MySQL", "sa", "")) {
            connection.createStatement().execute("create table personalization_fields (id bigint primary key)");
            try (var input=getClass().getResourceAsStream("/db/migration/V47__personalization_images.sql")) {
                RunScript.execute(connection,new StringReader(new String(input.readAllBytes(),StandardCharsets.UTF_8)));
            }
            connection.createStatement().execute("insert into personalization_fields (id,image_options_json) values (1,'[]')");
            connection.createStatement().execute("insert into personalization_images (id,store_id,product_id,field_id,bucket,preview_key,thumbnail_key,state,created_at,updated_at,retry_at) values ('image',1,1,1,'bucket','preview','thumbnail','DELETING',current_timestamp,current_timestamp,current_timestamp)");
            connection.createStatement().execute("delete from personalization_fields where id=1");
            try(var result=connection.createStatement().executeQuery("select count(*) from personalization_images")) {
                result.next();assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
    }
}
