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
    @Test void migrationSetsImageEntitlementsForAllPlans() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:plan-images-migration;MODE=MySQL","sa","")) {
            connection.createStatement().execute("create table plan_assets (plan_type varchar(40), product_limit bigint)");
            connection.createStatement().execute("insert into plan_assets values ('FREE',1),('FREE_GRATIS',1),('PREMIUM',10),('PREMIUM_PLUS',50),('PREMIUM_ULTRA',-1)");
            try(var input=getClass().getResourceAsStream("/db/migration/V48__plan_image_limits.sql")) {
                RunScript.execute(connection,new StringReader(new String(input.readAllBytes(),StandardCharsets.UTF_8)));
            }
            try(var rows=connection.createStatement().executeQuery("select plan_type,image_product_limit,image_option_limit from plan_assets")) {
                var expected=java.util.Map.of("FREE",new long[]{0,0},"FREE_GRATIS",new long[]{0,0},"PREMIUM",new long[]{1,3},"PREMIUM_PLUS",new long[]{50,8},"PREMIUM_ULTRA",new long[]{-1,25});
                while(rows.next()) { assertThat(rows.getLong(2)).isEqualTo(expected.get(rows.getString(1))[0]);assertThat(rows.getInt(3)).isEqualTo(expected.get(rows.getString(1))[1]); }
            }
        }
    }

}
