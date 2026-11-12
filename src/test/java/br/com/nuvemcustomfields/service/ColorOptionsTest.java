package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.FieldForm;
import br.com.nuvemcustomfields.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ColorOptionsTest {
    @Test void normalizesNamesAndHexWithoutChangingOrder() {
        var options=ColorOptions.parse("[{\"label\":\" Azul marinho \",\"color\":\"#123abc\"},{\"label\":\"Branco\",\"color\":\"#ffffff\"}]");
        assertThat(options).containsExactly(new ColorOptions.Option("Azul marinho","#123ABC"),new ColorOptions.Option("Branco","#FFFFFF"));
        assertThat(ColorOptions.parse(ColorOptions.normalize("[{\"label\":\"Preto\",\"color\":\"#000000\"}]"))).hasSize(1);
    }
    @Test void rejectsEmptyDuplicateAndUnsafeOptions() {
        for(String value:List.of("[]","null","{}","[null]","not json",
                "[{\"label\":\"\",\"color\":\"#ffffff\"}]",
                "[{\"label\":\"Azul\",\"color\":\"red\"}]",
                "[{\"label\":\"Azul\",\"color\":\"#fff\"}]",
                "[{\"label\":\"Azul\",\"color\":\"#000000\"},{\"label\":\" azul \",\"color\":\"#ffffff\"}]")) {
            assertThatThrownBy(()->ColorOptions.parse(value)).hasMessage("color.options.invalid");
            var form=new FieldForm();form.setFieldType(FieldType.COLOR_SELECT);form.setOptionsText(value);assertThat(form.isColorOptionsValid()).isFalse();
        }
        String color="{\"label\":\"%s\",\"color\":\"#ffffff\"}";
        assertThatThrownBy(()->ColorOptions.parse("["+String.join(",",java.util.stream.IntStream.range(0,26).mapToObj(i->color.formatted("Cor "+i)).toList())+"]")).hasMessage("color.options.invalid");
    }
    @Test void orderItemRetainsHumanReadableNameAfterOptionIsDeleted() throws Exception {
        var rule=new PersonalizationRule();rule.setProductId(123L);rule.setStoreId(55L);
        var field=new PersonalizationField();field.setRule(rule);field.setFieldType(FieldType.COLOR_SELECT);field.setLabel("Cor da estampa");
        field.setOptionsText("[{\"label\":\"Branco\",\"color\":\"#FFFFFF\"}]");
        var matcher=new OrderPersonalization(List.of(field));
        var item=new ObjectMapper().readTree("{\"product_id\":123,\"properties\":{\"Cor da estampa\":\"Azul marinho\"}}");
        assertThat(matcher.productProperties(item)).containsExactly("Cor da estampa: Azul marinho");
    }
}
