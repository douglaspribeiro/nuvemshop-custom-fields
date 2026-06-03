package br.com.nuvemcustomfields.dto;

import java.util.List;

public record PersonalizationResponse(boolean enabled, List<FieldResponse> fields) {

    public static PersonalizationResponse disabled() {
        return new PersonalizationResponse(false, List.of());
    }
}
