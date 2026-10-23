package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DlocalGoWebhookServiceTest {
    @Test void persistsIdentifiersOnlyAndReconcilesCurrentStateFromApi() {
        var gateway=mock(DlocalGoGateway.class); var events=mock(PaymentWebhookEventRepository.class);
        var subscriptions=mock(PaymentSubscriptionService.class); var locals=mock(PaymentSubscriptionRepository.class);
        var service=new DlocalGoWebhookService(gateway,events,subscriptions,locals);
        var local=new PaymentSubscription(); local.setStoreId(42L); local.setProvider(PaymentProviderType.DLOCAL_GO);
        local.setProviderEnvironment(PaymentEnvironment.SANDBOX);
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX); when(gateway.operational()).thenReturn(true);
        when(locals.findByExternalReference("ref")).thenReturn(Optional.of(local));
        when(gateway.verifyNotification("body","signature",null,"ref")).thenReturn(new GatewayNotification(PaymentProviderType.DLOCAL_GO,
                "event","payment.updated","ref",null,PaymentEnvironment.SANDBOX,Instant.now(),null));
        service.receive("body","signature","ref");
        var captured=org.mockito.ArgumentCaptor.forClass(PaymentWebhookEvent.class); verify(events).saveAndFlush(captured.capture());
        var event=captured.getValue(); assertThat(event.getProviderResourceId()).isEqualTo("ref");
        when(events.findTop50ByProviderAndProviderEnvironmentAndStatusInAndNextAttemptAtLessThanEqualOrderByReceivedAtAsc(
                eq(PaymentProviderType.DLOCAL_GO),eq(PaymentEnvironment.SANDBOX),any(),any())).thenReturn(List.of(event));
        when(events.claimProvider(any(),eq(PaymentProviderType.DLOCAL_GO),eq(PaymentEnvironment.SANDBOX),any(),any())).thenReturn(1);
        when(subscriptions.reconcile(42L)).thenReturn(local); service.processPending();
        verify(subscriptions).reconcile(42L); assertThat(event.getStatus()).isEqualTo(PaymentWebhookStatus.PROCESSED);
    }
    @Test void neverQueuesAnEventForAnotherProviderOrEnvironment() {
        var gateway=mock(DlocalGoGateway.class); var events=mock(PaymentWebhookEventRepository.class);
        var locals=mock(PaymentSubscriptionRepository.class);
        var service=new DlocalGoWebhookService(gateway,events,mock(PaymentSubscriptionService.class),locals);
        when(gateway.verifyNotification(any(),any(),any(),any())).thenReturn(new GatewayNotification(PaymentProviderType.DLOCAL_GO,"event","payment.updated","ref"));
        var local=new PaymentSubscription(); local.setProvider(PaymentProviderType.STRIPE);
        when(locals.findByExternalReference("ref")).thenReturn(Optional.of(local));
        assertThatThrownBy(() -> service.receive("body","sig","ref")).isInstanceOf(IllegalArgumentException.class);
        verify(events,never()).saveAndFlush(any());
    }
}
