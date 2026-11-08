package br.com.nuvemcustomfields.entity;

public enum FieldType {
    TEXT,
    NUMBER,
    SELECT,
    IMAGE_SELECT,
    CHECKBOX,
    TEXTAREA,
    // Read compatibility only: this rollback build does not create buyer uploads.
    FILE_UPLOAD
}
