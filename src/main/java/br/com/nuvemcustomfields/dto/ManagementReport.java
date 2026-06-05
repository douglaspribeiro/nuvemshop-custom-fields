package br.com.nuvemcustomfields.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public record ManagementReport(
        long freeStores,
        long premiumStores,
        long premiumPlusStores,
        BigDecimal estimatedMrr,
        BigDecimal projectedMonthPayments,
        long projectedMonthPaymentCount,
        YearMonth projectedMonth,
        long planEvents,
        long configuredProducts,
        long configuredFields
) {
}
