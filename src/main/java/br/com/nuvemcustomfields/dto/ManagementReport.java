package br.com.nuvemcustomfields.dto;

import java.math.BigDecimal;

public record ManagementReport(
        long freeStores,
        long premiumStores,
        long premiumPlusStores,
        long premiumUltraStores,
        BigDecimal estimatedMrr,
        long planEvents,
        long configuredProducts,
        long configuredFields
) {
}
