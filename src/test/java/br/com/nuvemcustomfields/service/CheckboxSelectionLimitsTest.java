package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.*;
import br.com.nuvemcustomfields.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CheckboxSelectionLimitsTest {
    private FieldForm form(Integer min, Integer max) {
        var form = new FieldForm(); form.setLabel("Flores"); form.setFieldType(FieldType.CHECKBOX);
        form.setOptionsText(java.util.stream.IntStream.rangeClosed(1, 40).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining("\n")));
        form.setMinSelections(min); form.setMaxSelections(max); return form;
    }
    @Test void validatesExactMinimumMaximumAndExistingCheckboxes() {
        assertThat(form(7,7).isCheckboxSelectionsValid()).isTrue();
        assertThat(form(null,7).isCheckboxSelectionsValid()).isTrue();
        assertThat(form(7,null).isCheckboxSelectionsValid()).isTrue();
        assertThat(form(0,7).isCheckboxSelectionsValid()).isTrue();
        var existing=form(null,null); existing.setOptionsText(null);
        assertThat(existing.isCheckboxSelectionsValid()).isTrue();
        for (var limits : new Integer[][] {{8,7},{-1,7},{0,0},{41,null},{null,41}})
            assertThat(form(limits[0],limits[1]).isCheckboxSelectionsValid()).isFalse();
        var duplicate=form(2,2); duplicate.setOptionsText("1\n1");
        assertThat(duplicate.isCheckboxSelectionsValid()).isFalse();
        duplicate.setOptionsText(null);
        assertThat(duplicate.isCheckboxSelectionsValid()).isFalse();
    }
    @Test void templatesAndStorefrontKeepLimitsAndOldTemplateJsonStillLoads() throws Exception {
        var json=new ObjectMapper();
        var definition=TemplateFieldDefinition.from(form(7,7));
        var restored=json.readValue(json.writeValueAsString(definition),TemplateFieldDefinition.class).toForm(0);
        assertThat(restored.getMinSelections()).isEqualTo(7);
        assertThat(restored.getMaxSelections()).isEqualTo(7);
        var field=new PersonalizationField();field.setLabel("Flores");field.setFieldType(FieldType.CHECKBOX);
        field.setOptionsText("1\n2");field.setMinSelections(2);field.setMaxSelections(2);
        assertThat(TemplateFieldDefinition.from(field).toForm(0).getMaxSelections()).isEqualTo(2);
        var response=FieldResponse.from(field);
        assertThat(response.minSelections()).isEqualTo(2);
        assertThat(response.maxSelections()).isEqualTo(2);
        var old=json.readValue("{\"label\":\"Antigo\",\"fieldType\":\"CHECKBOX\",\"maxLength\":100}",TemplateFieldDefinition.class);
        assertThat(old.minSelections()).isNull(); assertThat(old.maxSelections()).isNull();
    }
}
