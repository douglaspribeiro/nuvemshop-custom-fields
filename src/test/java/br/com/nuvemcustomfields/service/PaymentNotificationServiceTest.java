package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentNotificationOutbox;
import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentSubscription;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.payment.GatewayInvoice;
import br.com.nuvemcustomfields.repository.PaymentNotificationOutboxRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentNotificationServiceTest {
    private final PaymentNotificationOutboxRepository outbox = mock(PaymentNotificationOutboxRepository.class);
    private final DiscordPaymentWebhookClient discord = mock(DiscordPaymentWebhookClient.class);
    private final PaymentNotificationService service = new PaymentNotificationService(outbox, discord);

    @Test
    void queuesConfirmedPaymentOnlyOnce() {
        PaymentSubscription subscription = new PaymentSubscription();
        subscription.setStoreId(123L);
        subscription.setProvider(PaymentProviderType.EFI);
        subscription.setPlan(PlanType.PREMIUM);
        subscription.setCurrency("BRL");
        subscription.setAmountValue(new BigDecimal("19.99"));
        GatewayInvoice invoice = new GatewayInvoice("charge-1", "sub-1", "charge-1", "approved");

        service.enqueue(subscription, invoice);

        ArgumentCaptor<PaymentNotificationOutbox> saved = ArgumentCaptor.forClass(PaymentNotificationOutbox.class);
        verify(outbox).save(saved.capture());
        assertThat(saved.getValue().getPaymentId()).isEqualTo("charge-1");
        assertThat(saved.getValue().getStoreId()).isEqualTo(123L);
        when(outbox.existsByProviderAndPaymentId(PaymentProviderType.EFI, "charge-1")).thenReturn(true);
        service.enqueue(subscription, invoice);
        verify(outbox, times(1)).save(any());
    }

    @Test
    void doesNotQueueWaitingCharge() {
        service.enqueue(new PaymentSubscription(), new GatewayInvoice("charge-1", "sub-1", "charge-1", "waiting"));
        verifyNoInteractions(outbox);
    }

    @Test
    void retriesFailedDiscordDeliveryWithoutLosingPayment() {
        PaymentNotificationOutbox notification = new PaymentNotificationOutbox();
        notification.setProvider(PaymentProviderType.EFI);
        notification.setPaymentId("charge-1");
        when(discord.configured()).thenReturn(true);
        when(outbox.findDue(any(), any())).thenReturn(List.of(notification));
        doThrow(new IllegalStateException("Discord unavailable")).when(discord).send(notification);

        Instant before = Instant.now();
        service.deliverDue();

        assertThat(notification.getDeliveredAt()).isNull();
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getNextAttemptAt()).isAfter(before);
    }

    @Test
    void marksSuccessfulDelivery() {
        PaymentNotificationOutbox notification = new PaymentNotificationOutbox();
        notification.setProvider(PaymentProviderType.EFI);
        notification.setPaymentId("charge-1");
        when(discord.configured()).thenReturn(true);
        when(outbox.findDue(any(), any())).thenReturn(List.of(notification));

        service.deliverDue();

        assertThat(notification.getDeliveredAt()).isNotNull();
        verify(discord).send(notification);
    }
}
