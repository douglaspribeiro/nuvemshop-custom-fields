package br.com.nuvemcustomfields.dto;

import java.util.List;

public record PersonalizedOrderSummary(
        Long id,
        String number,
        String createdAt,
        List<Item> items
) {
    public record Item(int position, String name, String imageUrl, int quantity, List<String> properties) {
    }
}
