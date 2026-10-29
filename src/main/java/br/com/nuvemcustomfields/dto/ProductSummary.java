package br.com.nuvemcustomfields.dto;

public record ProductSummary(Long id, String name, String imageUrl) {
    public ProductSummary(Long id, String name) {
        this(id, name, null);
    }
}
