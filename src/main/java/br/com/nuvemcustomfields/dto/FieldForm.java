package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.entity.FieldType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FieldForm {

    @NotBlank
    private String label;

    @NotNull
    private FieldType fieldType = FieldType.TEXT;

    private boolean required;

    @Min(1)
    @Max(500)
    private Integer maxLength = 100;

    private String placeholder;

    private String validationPattern;

    @Min(0)
    private Integer sortOrder = 0;

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

    @AssertTrue(message = "Regex invalida")
    public boolean isValidationPatternValid() {
        if (validationPattern == null || validationPattern.isBlank()) {
            return true;
        }
        try {
            Pattern.compile(validationPattern);
            return true;
        } catch (PatternSyntaxException exception) {
            return false;
        }
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
