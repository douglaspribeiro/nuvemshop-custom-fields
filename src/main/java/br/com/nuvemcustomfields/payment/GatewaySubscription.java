package br.com.nuvemcustomfields.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record GatewaySubscription(
        String id,
        String checkoutResourceId,
        String externalReference,
        String status,
        String currency,
        BigDecimal amount,
        Instant nextPaymentAt,
        String customerId,
        String priceId,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant cancellationEffectiveAt
) {
    public GatewaySubscription(String id, String checkoutResourceId, String externalReference, String status,
                               String currency, BigDecimal amount, Instant nextPaymentAt) {
        this(id, checkoutResourceId, externalReference, status, currency, amount, nextPaymentAt,
                null, null, null, null, null);
    }
}
