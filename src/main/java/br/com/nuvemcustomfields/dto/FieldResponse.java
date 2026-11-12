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
        List<ImageOptionResponse> imageOptions,
        boolean showImageZoom,
        Integer mobileImageColumns,
        Long id,
        int maxFiles,
        long maxFileBytes,
        List<br.com.nuvemcustomfields.service.ColorOptions.Option> colorOptions
) {
    public FieldResponse(String label, FieldType type, boolean required, Integer maxLength, String placeholder,
            String pattern, String property, List<String> options, List<ImageOptionResponse> images, boolean zoom, Integer columns,
            Long id, int maxFiles, long maxFileBytes) {
        this(label,type,required,maxLength,placeholder,pattern,property,options,images,zoom,columns,id,maxFiles,maxFileBytes,List.of());
    }
    public FieldResponse(String label, FieldType type, boolean required, Integer maxLength, String placeholder,
            String pattern, String property, List<String> options, List<ImageOptionResponse> images, boolean zoom, Integer columns) {
        this(label,type,required,maxLength,placeholder,pattern,property,options,images,zoom,columns,null,1,10_000_000L);
    }

    public static FieldResponse from(PersonalizationField field) { return from(field, 25); }
    public static FieldResponse from(PersonalizationField field, int imageOptionLimit) {
        return from(field, imageOptionLimit, br.com.nuvemcustomfields.entity.PlanType.PREMIUM_PLUS);
    }
    public static FieldResponse from(PersonalizationField field, int imageOptionLimit, br.com.nuvemcustomfields.entity.PlanType plan) {
        var images = field.getFieldType() == FieldType.IMAGE_SELECT ? field.imageOptions().stream().limit(imageOptionLimit).toList() : List.<ImageOption>of();
        return new FieldResponse(
                field.getLabel(),
                field.getFieldType(),
                field.isRequired() || field.getFieldType() == FieldType.SELECT,
                field.getFieldType() == FieldType.FILE_UPLOAD ? null : field.getMaxLength(),
                field.getPlaceholder(),
                field.getValidationPattern(),
                field.getFieldType() == FieldType.FILE_UPLOAD ? "_ncf_file_" + field.getId() : propertyName(field.getLabel()),
                field.getFieldType() == FieldType.IMAGE_SELECT ? images.stream().map(ImageOption::label).toList()
                        : field.getFieldType() == FieldType.CHECKBOX ? field.options().stream().distinct().toList() : field.options(),
                field.getFieldType() == FieldType.IMAGE_SELECT ? images.stream().map(option -> {
                    String url = "/public/stores/" + field.getRule().getStoreId() + "/images/" + option.id();
                    return new ImageOptionResponse(option.label(), url + "?size=thumbnail", url);
                }).toList() : List.of(),
                field.isShowImageZoom(),
                field.getMobileImageColumns(),
                field.getId(),
                plan == br.com.nuvemcustomfields.entity.PlanType.PREMIUM_ULTRA ? field.getMaxFiles() : 1,
                br.com.nuvemcustomfields.service.BuyerUploadPolicy.maxFileBytes(plan),
                field.getFieldType() == FieldType.COLOR_SELECT ? br.com.nuvemcustomfields.service.ColorOptions.parse(field.getOptionsText()) : List.of()
        );
    }

    public record ImageOptionResponse(String label, String thumbnailUrl, String imageUrl) {}

    private static String propertyName(String label) {
        return label.replace('[', '(').replace(']', ')').strip();
    }
}
