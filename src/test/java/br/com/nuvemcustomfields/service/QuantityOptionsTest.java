package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.*;
import br.com.nuvemcustomfields.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.List;

class QuantityOptionsTest {
    FieldForm form() {
        var f=new FieldForm();f.setLabel("Flores");f.setFieldType(FieldType.QUANTITY_SELECT);
        f.setQuantityColumns(3);f.setOptionsText("Rosa\nMargarida");f.setMinSelections(7);f.setMaxSelections(7);return f;
    }
    @Test void totalLimitsAreIndependentOfTheNumberOfDistinctOptions() {
        var f=form();assertThat(f.isCheckboxSelectionsValid()).isTrue();
        f.setMinSelections(null);f.setMaxSelections(null);assertThat(f.isCheckboxSelectionsValid()).isTrue();
        f.setOptionsText(" ");assertThat(f.isCheckboxSelectionsValid()).isFalse();
        f=form();f.setMinSelections(-1);assertThat(f.isCheckboxSelectionsValid()).isFalse();
        f=form();f.setMaxSelections(0);assertThat(f.isCheckboxSelectionsValid()).isFalse();
        f=form();f.setMinSelections(8);assertThat(f.isCheckboxSelectionsValid()).isFalse();
        f=form();f.setValidationPattern("[");assertThat(f.isValidationPatternValid()).isTrue();
    }
    @Test void definitionRoundTripAndOrderPropertiesKeepTheComposition() throws Exception {
        var definition=TemplateFieldDefinition.from(form());var json=new ObjectMapper();
        var restored=json.readValue(json.writeValueAsString(definition),TemplateFieldDefinition.class).toForm(0);
        assertThat(restored.getFieldType()).isEqualTo(FieldType.QUANTITY_SELECT);
        assertThat(restored.getQuantityColumns()).isEqualTo(3);
        assertThat(restored.getMinSelections()).isEqualTo(7);assertThat(restored.getMaxSelections()).isEqualTo(7);
        var rule=new PersonalizationRule();rule.setStoreId(1L);rule.setProductId(123L);
        var field=new PersonalizationField();field.setRule(rule);field.setLabel("Flores");field.setFieldType(FieldType.QUANTITY_SELECT);
        field.setOptionsText("Rosa\nMargarida");field.setMinSelections(7);field.setMaxSelections(7);
        assertThat(FieldResponse.from(field).options()).containsExactly("Rosa","Margarida");
        var order=json.readTree("{\"product_id\":123,\"properties\":{\"Flores\":\"Rosa × 3; Margarida × 4\"}}");
        assertThat(new OrderPersonalization(List.of(field)).productProperties(order)).containsExactly("Flores: Rosa × 3; Margarida × 4");
    }
    @Test void columnDefaultsAndValidationKeepExistingDefinitionsCompatible() throws Exception {
        var json=new ObjectMapper();
        var old=json.readValue("{\"label\":\"Flores\",\"fieldType\":\"QUANTITY_SELECT\",\"maxLength\":100}",TemplateFieldDefinition.class);
        assertThat(old.quantityColumns()).isEqualTo(1);
        assertThat(new PersonalizationField().getQuantityColumns()).isEqualTo(1);
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();var f=form();
            for(int columns:new int[]{1,2,3}){f.setQuantityColumns(columns);assertThat(validator.validate(f)).isEmpty();}
            for(int columns:new int[]{0,4}){f.setQuantityColumns(columns);assertThat(validator.validate(f)).isNotEmpty();}
        }
    }

}
