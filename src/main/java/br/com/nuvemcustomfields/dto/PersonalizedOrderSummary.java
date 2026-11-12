package br.com.nuvemcustomfields.dto;

import java.util.List;

public record PersonalizedOrderSummary(
        Long id,
        String number,
        String createdAt,
        List<Item> items
) {
    public record Item(int position, String productId, String name, String imageUrl, int quantity, List<String> properties, List<String> uploadIds, List<Property> fieldGroups) {
    }
    public record Property(String label,String value,List<Upload> files) {}
    public record Upload(String id,String name) {
    }
}
