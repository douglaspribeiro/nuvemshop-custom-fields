package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.PaymentWebhookEventRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DodoLiveWebhookServiceTest {
    final DodoLiveGateway gateway = mock(DodoLiveGateway.class);
    final PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
    final PaymentSubscriptionService subscriptions = mock(PaymentSubscriptionService.class);
    final DodoLiveWebhookService service = new DodoLiveWebhookService(gateway, events, subscriptions);
    @Test void verifiesAndPersistsTheEventOnceBeforeAnySubscriptionChange() {
        when(gateway.verifyNotification("body","signature","id","time")).thenReturn(new GatewayNotification(
                PaymentProviderType.DODO,"DODO:PRODUCTION:id","payment.succeeded","pay_test","id",PaymentEnvironment.PRODUCTION,Instant.now(),"body"));
        var saved = new HashMap<String,PaymentWebhookEvent>();
        when(events.findByEventKey(anyString())).thenAnswer(i -> Optional.ofNullable(saved.get(i.getArgument(0))));
        when(events.saveAndFlush(any())).thenAnswer(i -> { PaymentWebhookEvent event = i.getArgument(0); saved.put(event.getEventKey(),event); return event; });
        service.receive("body","signature","id","time"); service.receive("body","signature","id","time");
        verify(events,times(1)).saveAndFlush(any()); verifyNoInteractions(subscriptions);
        assertThat(saved.values().iterator().next().getProviderEnvironment()).isEqualTo(PaymentEnvironment.PRODUCTION);
    }
    @Test void processingClaimsTheSandboxEventAndSchedulesRetryOnApiFailure() {
        when(gateway.provider()).thenReturn(PaymentProviderType.DODO);
        when(gateway.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
        when(gateway.operational()).thenReturn(true);
        var event = new PaymentWebhookEvent(); event.setEventKey("DODO:PRODUCTION:id");
        event.setProvider(PaymentProviderType.DODO); event.setProviderEnvironment(PaymentEnvironment.PRODUCTION);
        event.setEventType("payment.succeeded"); event.setProviderResourceId("pay_test");
        when(events.findTop50ByProviderAndProviderEnvironmentAndStatusInAndNextAttemptAtLessThanEqualOrderByReceivedAtAsc(
                eq(PaymentProviderType.DODO),eq(PaymentEnvironment.PRODUCTION),any(),any())).thenReturn(List.of(event));
        when(events.claimProvider(isNull(),eq(PaymentProviderType.DODO),eq(PaymentEnvironment.PRODUCTION),any(),any())).thenReturn(1);
        when(subscriptions.synchronizeFromInvoice(PaymentProviderType.DODO,"pay_test")).thenThrow(new PaymentGatewayException("timeout"));
        service.processPending();
        assertThat(event.getStatus()).isEqualTo(PaymentWebhookStatus.FAILED);
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now()); verify(events).saveAndFlush(event);
    }
}
