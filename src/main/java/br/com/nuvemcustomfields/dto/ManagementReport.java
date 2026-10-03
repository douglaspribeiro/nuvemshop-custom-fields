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
        long configuredFields,
        java.time.YearMonth projectedMonth,
        java.util.Map<String, BigDecimal> mrrByCurrency,
        java.util.Map<String, BigDecimal> projectedPaymentsByCurrency,
        java.util.Map<String, Long> projectedStoresByCurrency
) {
}
