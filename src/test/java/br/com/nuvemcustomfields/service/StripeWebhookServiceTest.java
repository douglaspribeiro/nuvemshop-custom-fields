package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.PaymentWebhookEventRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StripeWebhookServiceTest {
    final StripeGateway gateway = mock(StripeGateway.class);
    final PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
    final PaymentSubscriptionService subscriptions = mock(PaymentSubscriptionService.class);
    final StripeWebhookService service = new StripeWebhookService(gateway,events,subscriptions);
    @Test void persistsOnlyVerifiedIdentifiersAndDeduplicatesDelivery() {
        var notification = new GatewayNotification(PaymentProviderType.STRIPE,"STRIPE:SANDBOX:evt_1","customer.subscription.updated","sub_1",null,PaymentEnvironment.SANDBOX,Instant.now(),"{private-data}");
        when(gateway.verifyNotification("body","signature",null,null)).thenReturn(notification);
        service.receive("body","signature");
        var capture = org.mockito.ArgumentCaptor.forClass(PaymentWebhookEvent.class); verify(events).saveAndFlush(capture.capture());
        assertThat(capture.getValue().getProviderEnvironment()).isEqualTo(PaymentEnvironment.SANDBOX);
        assertThat(capture.getValue().getPayloadJson()).isNull();
        when(events.findByEventKey(notification.eventKey())).thenReturn(Optional.of(capture.getValue()));
        service.receive("body","signature"); verify(events,times(1)).saveAndFlush(any()); verifyNoInteractions(subscriptions);
    }
    @Test void oldInvoiceNotificationReconcilesCurrentSubscriptionInsteadOfReplayingOldPayment() {
        var event = pending("invoice.payment_failed","in_old");
        when(gateway.getInvoice("in_old")).thenReturn(new GatewayInvoice("in_old","sub_1","in_old","open"));
        when(subscriptions.synchronizeFromSubscription(PaymentProviderType.STRIPE,"sub_1")).thenReturn(new PaymentSubscription());
        service.processPending();
        verify(subscriptions).synchronizeFromSubscription(PaymentProviderType.STRIPE,"sub_1");
        verify(subscriptions,never()).synchronizeFromInvoice(any(),anyString());
        assertThat(event.getStatus()).isEqualTo(PaymentWebhookStatus.PROCESSED);
    }
    @Test void failedLeasePreventsConcurrentProcessing() {
        pending("customer.subscription.updated","sub_1");
        when(events.claimProvider(any(),any(),any(),any(),any())).thenReturn(0);
        service.processPending(); verifyNoInteractions(subscriptions);
    }
    @Test void transientFailureIsRetainedForRetryWithoutLeakingProviderData() {
        var event = pending("customer.subscription.updated","sub_1");
        when(subscriptions.synchronizeFromSubscription(any(),any())).thenThrow(new PaymentGatewayException("private provider response"));
        service.processPending();
        assertThat(event.getStatus()).isEqualTo(PaymentWebhookStatus.FAILED);
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(event.getLastError()).doesNotContain("private");
    }
    PaymentWebhookEvent pending(String type,String id) {
        var event = new PaymentWebhookEvent(); event.setProvider(PaymentProviderType.STRIPE);
        event.setProviderEnvironment(PaymentEnvironment.SANDBOX); event.setEventType(type); event.setProviderResourceId(id);
        when(gateway.operational()).thenReturn(true); when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(events.findTop50ByProviderAndProviderEnvironmentAndStatusInAndNextAttemptAtLessThanEqualOrderByReceivedAtAsc(eq(PaymentProviderType.STRIPE),eq(PaymentEnvironment.SANDBOX),any(),any())).thenReturn(List.of(event));
        when(events.claimProvider(any(),eq(PaymentProviderType.STRIPE),eq(PaymentEnvironment.SANDBOX),any(),any())).thenReturn(1);
        return event;
    }
}
