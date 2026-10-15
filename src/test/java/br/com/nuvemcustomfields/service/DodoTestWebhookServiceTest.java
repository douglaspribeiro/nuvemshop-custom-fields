package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.PaymentWebhookEventRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DodoTestWebhookServiceTest {
    final DodoTestGateway gateway = mock(DodoTestGateway.class);
    final PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
    final PaymentSubscriptionService subscriptions = mock(PaymentSubscriptionService.class);
    final DodoTestWebhookService service = new DodoTestWebhookService(gateway, events, subscriptions);
    @Test void verifiesAndPersistsTheEventOnceBeforeAnySubscriptionChange() {
        when(gateway.verifyNotification("body","signature","id","time")).thenReturn(new GatewayNotification(
                PaymentProviderType.DODO_TEST,"DODO_TEST:SANDBOX:id","payment.succeeded","pay_test","id",PaymentEnvironment.SANDBOX,Instant.now(),"body"));
        var saved = new HashMap<String,PaymentWebhookEvent>();
        when(events.findByEventKey(anyString())).thenAnswer(i -> Optional.ofNullable(saved.get(i.getArgument(0))));
        when(events.saveAndFlush(any())).thenAnswer(i -> { PaymentWebhookEvent event = i.getArgument(0); saved.put(event.getEventKey(),event); return event; });
        service.receive("body","signature","id","time"); service.receive("body","signature","id","time");
        verify(events,times(1)).saveAndFlush(any()); verifyNoInteractions(subscriptions);
        assertThat(saved.values().iterator().next().getProviderEnvironment()).isEqualTo(PaymentEnvironment.SANDBOX);
    }
    @Test void processingClaimsTheSandboxEventAndSchedulesRetryOnApiFailure() {
        when(gateway.provider()).thenReturn(PaymentProviderType.DODO_TEST);
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(gateway.operational()).thenReturn(true);
        var event = new PaymentWebhookEvent(); event.setEventKey("DODO_TEST:SANDBOX:id");
        event.setProvider(PaymentProviderType.DODO_TEST); event.setProviderEnvironment(PaymentEnvironment.SANDBOX);
        event.setEventType("payment.succeeded"); event.setProviderResourceId("pay_test");
        when(events.findTop50ByProviderAndProviderEnvironmentAndStatusInAndNextAttemptAtLessThanEqualOrderByReceivedAtAsc(
                eq(PaymentProviderType.DODO_TEST),eq(PaymentEnvironment.SANDBOX),any(),any())).thenReturn(List.of(event));
        when(events.claimProvider(isNull(),eq(PaymentProviderType.DODO_TEST),eq(PaymentEnvironment.SANDBOX),any(),any())).thenReturn(1);
        when(subscriptions.synchronizeFromInvoice(PaymentProviderType.DODO_TEST,"pay_test")).thenThrow(new PaymentGatewayException("timeout"));
        service.processPending();
        assertThat(event.getStatus()).isEqualTo(PaymentWebhookStatus.FAILED);
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now()); verify(events).saveAndFlush(event);
    }
}
