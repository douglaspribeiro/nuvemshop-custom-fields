package br.com.nuvemcustomfields.dto;

import java.util.List;

public record PersonalizedOrderSummary(
        Long id,
        String number,
        String createdAt,
        List<String> properties
) {
}
