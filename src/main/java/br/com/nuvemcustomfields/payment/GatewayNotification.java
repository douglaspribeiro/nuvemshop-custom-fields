package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentEnvironment;
import java.time.Instant;

public record GatewayNotification(
        PaymentProviderType provider,
        String eventKey,
        String type,
        String resourceId,
        String notificationId,
        PaymentEnvironment environment,
        Instant occurredAt,
        String payload
) {
    public GatewayNotification(PaymentProviderType provider, String eventKey, String type, String resourceId) {
        this(provider, eventKey, type, resourceId, null, PaymentEnvironment.PRODUCTION, null, null);
    }
}
