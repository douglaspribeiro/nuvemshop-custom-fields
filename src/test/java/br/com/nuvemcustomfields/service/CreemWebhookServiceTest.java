package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.PaymentWebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreemWebhookServiceTest {
    final CreemGateway gateway = mock(CreemGateway.class);
    final PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
    final PaymentSubscriptionService subscriptions = mock(PaymentSubscriptionService.class);
    final CreemWebhookService service = new CreemWebhookService(gateway,events,subscriptions,new ObjectMapper());
    @Test void storesVerifiedEventAndDeduplicatesReceipt() {
        var notification = new GatewayNotification(PaymentProviderType.CREEM,"CREEM:SANDBOX:evt_1","subscription.paid","sub_1",null,PaymentEnvironment.SANDBOX,Instant.now(),"{}");
        when(gateway.verifyNotification("{}","signature",null,null)).thenReturn(notification);
        service.receive("{}","signature");
        var capture = org.mockito.ArgumentCaptor.forClass(PaymentWebhookEvent.class); verify(events).saveAndFlush(capture.capture());
        assertThat(capture.getValue().getProviderEnvironment()).isEqualTo(PaymentEnvironment.SANDBOX);
        when(events.findByEventKey(notification.eventKey())).thenReturn(Optional.of(capture.getValue()));
        service.receive("{}","signature"); verify(events,times(1)).saveAndFlush(any());
        verifyNoInteractions(subscriptions);
    }
    @Test void activeOnlySynchronizesWhilePaidChecksTheActualPayment() {
        when(gateway.operational()).thenReturn(true); when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        var active = event(1L,"subscription.active"); var paid = event(2L,"subscription.paid");
        when(events.findTop50ByProviderAndProviderEnvironmentAndStatusInAndNextAttemptAtLessThanEqualOrderByReceivedAtAsc(eq(PaymentProviderType.CREEM),eq(PaymentEnvironment.SANDBOX),any(),any())).thenReturn(List.of(active,paid));
        when(events.claimCreem(anyLong(),any(),any())).thenReturn(1);
        when(subscriptions.synchronizeFromSubscriptionWithoutPayment(PaymentProviderType.CREEM,"sub_1")).thenReturn(new PaymentSubscription());
        when(subscriptions.synchronizeFromSubscription(PaymentProviderType.CREEM,"sub_1")).thenReturn(new PaymentSubscription());
        service.processPending();
        verify(subscriptions).synchronizeFromSubscriptionWithoutPayment(PaymentProviderType.CREEM,"sub_1");
        verify(subscriptions).synchronizeFromSubscription(PaymentProviderType.CREEM,"sub_1");
        assertThat(active.getStatus()).isEqualTo(PaymentWebhookStatus.PROCESSED); assertThat(paid.getStatus()).isEqualTo(PaymentWebhookStatus.PROCESSED);
    }
    @Test void failedClaimCannotProcessTheSameEventTwice() {
        when(gateway.operational()).thenReturn(true); when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        var event = event(1L,"subscription.paid");
        when(events.findTop50ByProviderAndProviderEnvironmentAndStatusInAndNextAttemptAtLessThanEqualOrderByReceivedAtAsc(any(),any(),any(),any())).thenReturn(List.of(event));
        when(events.claimCreem(anyLong(),any(),any())).thenReturn(0);
        service.processPending(); verifyNoInteractions(subscriptions);
    }
    PaymentWebhookEvent event(Long id,String type) {
        var event = mock(PaymentWebhookEvent.class, CALLS_REAL_METHODS); when(event.getId()).thenReturn(id);
        event.setProvider(PaymentProviderType.CREEM); event.setEventType(type); event.setProviderResourceId("sub_1"); return event;
    }
}
