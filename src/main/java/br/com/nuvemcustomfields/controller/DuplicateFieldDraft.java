package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.dto.FieldForm;
import br.com.nuvemcustomfields.dto.TemplateFieldDefinition;
import br.com.nuvemcustomfields.entity.PersonalizationField;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class DuplicateFieldDraft {
    private static final int LABEL_LIMIT = 100;

    private DuplicateFieldDraft() {}

    static FieldForm create(PersonalizationField source, Collection<PersonalizationField> existing, String suffix) {
        FieldForm draft = TemplateFieldDefinition.from(source).toForm(0);
        Set<String> labels = new HashSet<>();
        int maxOrder = -1;
        for (PersonalizationField field : existing) {
            if (field.getLabel() != null) labels.add(field.getLabel().toLowerCase(Locale.ROOT));
            if (field.getSortOrder() != null) maxOrder = Math.max(maxOrder, field.getSortOrder());
        }
        String base = source.getLabel() == null ? "" : source.getLabel().strip();
        String localizedSuffix = suffix == null ? "" : suffix.strip();
        String candidate = fit(base, localizedSuffix, "");
        for (int copy = 2; labels.contains(candidate.toLowerCase(Locale.ROOT)); copy++) {
            candidate = fit(base, localizedSuffix, " (" + copy + ")");
        }
        draft.setLabel(candidate);
        draft.setSortOrder(maxOrder == Integer.MAX_VALUE ? Integer.MAX_VALUE : maxOrder + 1);
        return draft;
    }

    private static String fit(String base, String suffix, String discriminator) {
        String ending = (base.isEmpty() ? "" : " ") + suffix + discriminator;
        if (ending.length() > LABEL_LIMIT) ending = ending.substring(ending.length() - LABEL_LIMIT);
        int prefixLength = Math.max(0, LABEL_LIMIT - ending.length());
        int end = Math.min(base.length(), prefixLength);
        if (end > 0 && end < base.length() && Character.isHighSurrogate(base.charAt(end - 1))) end--;
        String prefix = base.substring(0, end).stripTrailing();
        return (prefix + ending).strip();
    }
}
