package br.com.nuvemcustomfields.dto;

import java.math.BigDecimal;

public record ManagementReport(
        long freeStores,
        long premiumStores,
        long premiumPlusStores,
        BigDecimal estimatedMrr,
        long planEvents,
        long configuredProducts,
        long configuredFields
) {
}
