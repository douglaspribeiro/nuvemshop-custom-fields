package br.com.nuvemcustomfields.dto;

import java.util.List;

public record DashboardSummary(
        long configuredProducts,
        long configuredFields,
        List<PersonalizedOrderSummary> personalizedOrders
) {
}
