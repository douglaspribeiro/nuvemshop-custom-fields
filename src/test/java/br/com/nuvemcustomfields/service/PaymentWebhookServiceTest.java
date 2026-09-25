package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentWebhookEvent;
import br.com.nuvemcustomfields.entity.PaymentWebhookStatus;
import br.com.nuvemcustomfields.payment.GatewayNotification;
import br.com.nuvemcustomfields.payment.EfiGateway;
import br.com.nuvemcustomfields.payment.PaymentGateway;
import br.com.nuvemcustomfields.repository.PaymentWebhookEventRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.*;

class PaymentWebhookServiceTest {
    @Test
    void ignoresAnEventThatWasAlreadyProcessed() {
        PaymentGatewayRouter router = mock(PaymentGatewayRouter.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
        PaymentSubscriptionService subscriptions = mock(PaymentSubscriptionService.class);
        GatewayNotification notification = new GatewayNotification(PaymentProviderType.MERCADO_PAGO,
                "MERCADO_PAGO:subscription_preapproval:10:updated", "subscription_preapproval", "sub-1");
        PaymentWebhookEvent processed = new PaymentWebhookEvent();
        processed.setStatus(PaymentWebhookStatus.PROCESSED);
        when(router.require(PaymentProviderType.MERCADO_PAGO)).thenReturn(gateway);
        when(gateway.verifyNotification("{}", "signature", "request", "sub-1")).thenReturn(notification);
        when(events.findByEventKey(notification.eventKey())).thenReturn(Optional.of(processed));

        new PaymentWebhookService(router, events, subscriptions, mock(EfiGateway.class))
                .receiveMercadoPago("{}", "signature", "request", "sub-1");

        verifyNoInteractions(subscriptions);
        verify(events, never()).save(any());
    }
}
