package br.com.nuvemcustomfields.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "personalization_fields")
public class PersonalizationField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private PersonalizationRule rule;

    @Column(nullable = false, length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 30)
    private FieldType fieldType = FieldType.TEXT;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "max_length", nullable = false)
    private Integer maxLength = 100;

    @Column(length = 150)
    private String placeholder;

    @Column(name = "validation_pattern", length = 255)
    private String validationPattern;

    @Column(name = "options_text", columnDefinition = "TEXT")
    private String optionsText;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public Long getId() {
        return id;
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
