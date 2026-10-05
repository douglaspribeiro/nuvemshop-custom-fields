package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.entity.FieldType;
import br.com.nuvemcustomfields.entity.PersonalizationField;

import java.util.List;

public record FieldResponse(
        String label,
        FieldType fieldType,
        boolean required,
        Integer maxLength,
        String placeholder,
        String validationPattern,
        String propertyName,
        List<String> options,
        List<ImageOptionResponse> imageOptions
) {

    public static FieldResponse from(PersonalizationField field) {
        return new FieldResponse(
                field.getLabel(),
                field.getFieldType(),
                field.isRequired(),
                field.getMaxLength(),
                field.getPlaceholder(),
                field.getValidationPattern(),
                propertyName(field.getLabel()),
                field.options(),
                field.getFieldType() == FieldType.IMAGE_SELECT ? field.imageOptions().stream().map(option -> {
                    String url = "/public/stores/" + field.getRule().getStoreId() + "/images/" + option.id();
                    return new ImageOptionResponse(option.label(), url + "?size=thumbnail", url);
                }).toList() : List.of()
        );
    }

    public record ImageOptionResponse(String label, String thumbnailUrl, String imageUrl) {}

    private static String propertyName(String label) {
        return label.replace('[', '(').replace(']', ')').strip();
    }
}
