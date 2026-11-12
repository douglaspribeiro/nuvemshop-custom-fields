package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.entity.*;

/** Definitions only. Never serialize entities, buyer values or order/upload records. */
public record TemplateFieldDefinition(String label,FieldType fieldType,boolean required,Integer maxLength,
        String placeholder,String validationPattern,String optionsText,String imageOptionsJson,
        boolean showImageZoom,Integer mobileImageColumns,Integer maxFiles) {
    public TemplateFieldDefinition(String label,FieldType fieldType,boolean required,Integer maxLength,
            String placeholder,String validationPattern,String optionsText,String imageOptionsJson,
            boolean showImageZoom,Integer mobileImageColumns) {
        this(label,fieldType,required,maxLength,placeholder,validationPattern,optionsText,imageOptionsJson,showImageZoom,mobileImageColumns,1);
    }
    public java.util.List<br.com.nuvemcustomfields.service.ColorOptions.Option> colorOptions() {return fieldType==FieldType.COLOR_SELECT?br.com.nuvemcustomfields.service.ColorOptions.parse(optionsText):java.util.List.of();}
    public java.util.List<ImageOption> imageOptions() {return fieldType==FieldType.IMAGE_SELECT?br.com.nuvemcustomfields.service.ImageOptions.parse(imageOptionsJson):java.util.List.of();}
    public static TemplateFieldDefinition from(PersonalizationField field) {
        return new TemplateFieldDefinition(field.getLabel(),field.getFieldType(),field.isRequired(),field.getMaxLength(),
                field.getPlaceholder(),field.getValidationPattern(),field.getOptionsText(),field.getImageOptionsJson(),
                field.isShowImageZoom(),field.getMobileImageColumns(),field.getMaxFiles());
    }
    public static TemplateFieldDefinition from(FieldForm field) {
        return new TemplateFieldDefinition(field.getLabel(),field.getFieldType(),field.isRequired(),field.getMaxLength(),
                field.getPlaceholder(),field.getValidationPattern(),field.getOptionsText(),field.getImageOptionsJson(),
                field.isShowImageZoom(),field.getMobileImageColumns(),field.getMaxFiles());
    }
    public FieldForm toForm(int order) {
        var form=new FieldForm();form.setLabel(label);form.setFieldType(fieldType);form.setRequired(required);
        form.setMaxLength(maxLength);form.setPlaceholder(placeholder);form.setValidationPattern(validationPattern);
        form.setOptionsText(optionsText);form.setImageOptionsJson(imageOptionsJson);form.setShowImageZoom(showImageZoom);
        form.setMaxFiles(maxFiles);form.setMobileImageColumns(mobileImageColumns);form.setSortOrder(order);return form;
    }
}
