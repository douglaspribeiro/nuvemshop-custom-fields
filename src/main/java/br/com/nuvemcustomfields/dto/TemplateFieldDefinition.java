package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.entity.*;

/** Definitions only. Never serialize entities, buyer values or order/upload records. */
public record TemplateFieldDefinition(String label,FieldType fieldType,boolean required,Integer maxLength,
        String placeholder,String validationPattern,String optionsText,String imageOptionsJson,
        boolean showImageZoom,Integer mobileImageColumns) {
    public java.util.List<ImageOption> imageOptions() {return fieldType==FieldType.IMAGE_SELECT?br.com.nuvemcustomfields.service.ImageOptions.parse(imageOptionsJson):java.util.List.of();}
    public static TemplateFieldDefinition from(PersonalizationField field) {
        return new TemplateFieldDefinition(field.getLabel(),field.getFieldType(),field.isRequired(),field.getMaxLength(),
                field.getPlaceholder(),field.getValidationPattern(),field.getOptionsText(),field.getImageOptionsJson(),
                field.isShowImageZoom(),field.getMobileImageColumns());
    }
    public static TemplateFieldDefinition from(FieldForm field) {
        return new TemplateFieldDefinition(field.getLabel(),field.getFieldType(),field.isRequired(),field.getMaxLength(),
                field.getPlaceholder(),field.getValidationPattern(),field.getOptionsText(),field.getImageOptionsJson(),
                field.isShowImageZoom(),field.getMobileImageColumns());
    }
    public FieldForm toForm(int order) {
        var form=new FieldForm();form.setLabel(label);form.setFieldType(fieldType);form.setRequired(required);
        form.setMaxLength(maxLength);form.setPlaceholder(placeholder);form.setValidationPattern(validationPattern);
        form.setOptionsText(optionsText);form.setImageOptionsJson(imageOptionsJson);form.setShowImageZoom(showImageZoom);
        form.setMobileImageColumns(mobileImageColumns);form.setSortOrder(order);return form;
    }
}
