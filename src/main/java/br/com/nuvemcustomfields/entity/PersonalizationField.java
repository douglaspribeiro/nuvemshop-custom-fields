package br.com.nuvemcustomfields.entity;

import java.util.Arrays;
import java.util.List;

public class PersonalizationField {

    private Long id;
    private PersonalizationRule rule;
    private String label;
    private FieldType fieldType = FieldType.TEXT;
    private boolean required;
    private Integer maxLength = 100;
    private String placeholder;
    private String validationPattern;
    private String optionsText;
    private Integer sortOrder = 0;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PersonalizationRule getRule() {
        return rule;
    }

    public void setRule(PersonalizationRule rule) {
        this.rule = rule;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public FieldType getFieldType() {
        return fieldType;
    }

    public void setFieldType(FieldType fieldType) {
        this.fieldType = fieldType;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public String getValidationPattern() {
        return validationPattern;
    }

    public void setValidationPattern(String validationPattern) {
        this.validationPattern = validationPattern;
    }

    public String getOptionsText() {
        return optionsText;
    }

    public void setOptionsText(String optionsText) {
        this.optionsText = optionsText;
    }

    public List<String> options() {
        if (optionsText == null || optionsText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(optionsText.split("\\R"))
                .map(String::strip)
                .filter(option -> !option.isBlank())
                .toList();
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
