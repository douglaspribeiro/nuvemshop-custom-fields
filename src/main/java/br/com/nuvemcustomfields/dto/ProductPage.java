package br.com.nuvemcustomfields.dto;

import java.util.List;

/**
 * Uma pagina de produtos da API Nuvemshop. `totalCount` vem do header X-Total-Count
 * e pode ser -1 quando a API nao envia o header; nesse caso a navegacao depende de {@link #hasNext()}.
 */
public record ProductPage(
        List<ProductSummary> items,
        int page,
        int perPage,
        long totalCount,
        boolean hasNext,
        String query
) {

    public static ProductPage empty(int page, int perPage, String query) {
        return new ProductPage(List.of(), page, perPage, 0L, false, query);
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    public int previousPage() {
        return Math.max(page - 1, 1);
    }

    public int nextPage() {
        return page + 1;
    }

    public boolean totalKnown() {
        return totalCount >= 0;
    }

    public int totalPages() {
        if (!totalKnown() || perPage <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.ceilDiv(totalCount, perPage));
    }
}
