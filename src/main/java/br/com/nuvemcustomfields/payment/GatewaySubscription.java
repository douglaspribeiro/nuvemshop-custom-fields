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
        Instant cancellationEffectiveAt,
        String payerPhone
) {
    public GatewaySubscription(String id, String checkoutResourceId, String externalReference, String status,
                               String currency, BigDecimal amount, Instant nextPaymentAt, String customerId,
                               String priceId, Instant currentPeriodStart, Instant currentPeriodEnd,
                               Instant cancellationEffectiveAt) {
        this(id, checkoutResourceId, externalReference, status, currency, amount, nextPaymentAt,
                customerId, priceId, currentPeriodStart, currentPeriodEnd, cancellationEffectiveAt, null);
    }

    public GatewaySubscription withPayerPhone(String phone) {
        return new GatewaySubscription(id, checkoutResourceId, externalReference, status, currency, amount,
                nextPaymentAt, customerId, priceId, currentPeriodStart, currentPeriodEnd, cancellationEffectiveAt, phone);
    }

    public GatewaySubscription(String id, String checkoutResourceId, String externalReference, String status,
                               String currency, BigDecimal amount, Instant nextPaymentAt) {
        this(id, checkoutResourceId, externalReference, status, currency, amount, nextPaymentAt,
                null, null, null, null, null);
    }
}
