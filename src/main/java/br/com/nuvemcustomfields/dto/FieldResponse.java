package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.entity.FieldType;
import br.com.nuvemcustomfields.entity.PersonalizationField;

public record FieldResponse(
        String label,
        FieldType fieldType,
        boolean required,
        Integer maxLength,
        String placeholder
) {

    public static FieldResponse from(PersonalizationField field) {
        return new FieldResponse(
                field.getLabel(),
                field.getFieldType(),
                field.isRequired(),
                field.getMaxLength(),
                field.getPlaceholder()
        );
    }
}
