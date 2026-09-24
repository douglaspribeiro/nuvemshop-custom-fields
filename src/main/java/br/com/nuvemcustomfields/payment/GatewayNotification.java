package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.PaymentProviderType;

public record GatewayNotification(
        PaymentProviderType provider,
        String eventKey,
        String type,
        String resourceId
) {
}
