package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.entity.FieldType;

public record FieldTemplate(
        String label,
        FieldType fieldType,
        boolean required,
        Integer maxLength,
        String placeholder,
        String validationPattern,
        String optionsText
) {

    public FieldForm toForm(int sortOrder) {
        FieldForm form = new FieldForm();
        form.setLabel(label);
        form.setFieldType(fieldType);
        form.setRequired(required);
        form.setMaxLength(maxLength);
        form.setPlaceholder(placeholder);
        form.setValidationPattern(validationPattern);
        form.setOptionsText(optionsText);
        form.setSortOrder(sortOrder);
        return form;
    }
}
