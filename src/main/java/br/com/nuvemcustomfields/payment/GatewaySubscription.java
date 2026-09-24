package br.com.nuvemcustomfields.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record GatewaySubscription(
        String id,
        String externalReference,
        String status,
        String currency,
        BigDecimal amount,
        Instant nextPaymentAt
) {
}
