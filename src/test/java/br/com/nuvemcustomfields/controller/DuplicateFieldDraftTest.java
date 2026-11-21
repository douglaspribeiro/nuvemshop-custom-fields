package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.FieldType;
import br.com.nuvemcustomfields.entity.PersonalizationField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateFieldDraftTest {
    @Test
    void createsDistinctCopiesWithinLabelLimitAndAtEnd() {
        PersonalizationField source = field("Gift message", 3);
        PersonalizationField firstCopy = field("Gift message copy", 9);
        PersonalizationField secondCopy = field("Gift message copy (2)", 12);

        var draft = DuplicateFieldDraft.create(source, List.of(source, firstCopy, secondCopy), "copy");

        assertThat(draft.getLabel()).isEqualTo("Gift message copy (3)");
        assertThat(draft.getLabel()).hasSizeLessThanOrEqualTo(100);
        assertThat(draft.getSortOrder()).isEqualTo(13);
    }

    @Test
    void truncatesLongSourceToKeepLocalizedSuffixAndAddsRepeatedCopyMarker() {
        PersonalizationField source = field("x".repeat(120), 0);
        PersonalizationField firstCopy = field("x".repeat(94) + " copia", 4);

        var draft = DuplicateFieldDraft.create(source, List.of(source, firstCopy), "copia");

        assertThat(draft.getLabel()).hasSizeLessThanOrEqualTo(100);
        assertThat(draft.getLabel()).endsWith("copia (2)");
        assertThat(draft.getSortOrder()).isEqualTo(5);
    }


    @Test
    void avoidsCaseInsensitiveCollisionsAndSurrogateSplitsAtOrderLimit() {
        String base = "x".repeat(90) + "😀" + "z".repeat(10);
        String firstCandidate = "x".repeat(90) + "😀" + "z".repeat(3) + " copy";
        PersonalizationField source = field(base, Integer.MAX_VALUE);
        PersonalizationField existing = field(firstCandidate.toUpperCase(java.util.Locale.ROOT), Integer.MAX_VALUE);

        var draft = DuplicateFieldDraft.create(source, List.of(source, existing), "copy");

        assertThat(draft.getLabel()).isEqualTo("x".repeat(90) + " copy (2)");
        assertThat(draft.getLabel()).hasSizeLessThanOrEqualTo(100);
        assertThat(draft.getSortOrder()).isEqualTo(Integer.MAX_VALUE);
        assertThat(draft.getLabel()).doesNotMatch(".*[\\uD800-\\uDBFF]$");
    }

    private static PersonalizationField field(String label, int order) {
        var field = new PersonalizationField();
        field.setLabel(label);
        field.setFieldType(FieldType.TEXT);
        field.setSortOrder(order);
        return field;
    }
}
