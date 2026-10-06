package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentNotificationOutbox;
import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentSubscription;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.entity.UpgradeAdjustment;
import br.com.nuvemcustomfields.payment.GatewayInvoice;
import br.com.nuvemcustomfields.repository.PaymentNotificationOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    @ParameterizedTest
    @EnumSource(value = PaymentProviderType.class, names = {"EFI", "CREEM"})
    void retriesFailedDiscordDeliveryWithoutLosingPayment(PaymentProviderType provider) {
        PaymentNotificationOutbox notification = new PaymentNotificationOutbox();
        notification.setProvider(provider);
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

    @ParameterizedTest
    @EnumSource(value = PaymentProviderType.class, names = {"EFI", "CREEM"})
    void marksSuccessfulDelivery(PaymentProviderType provider) {
        PaymentNotificationOutbox notification = new PaymentNotificationOutbox();
        notification.setProvider(provider);
        notification.setPaymentId("charge-1");
        when(discord.configured()).thenReturn(true);
        when(outbox.findDue(any(), any())).thenReturn(List.of(notification));

        service.deliverDue();

        assertThat(notification.getDeliveredAt()).isNotNull();
        verify(discord).send(notification);
    }

    @Test
    void creemPaymentsAndRenewalsUseActualPaidAmountAndDeduplicateEachTransaction() {
        var subscription = creemSubscription();
        var first = new GatewayInvoice("tran_first", "sub_1", "tran_first", "paid", "USD", new BigDecimal("6.50"));
        var renewal = new GatewayInvoice("tran_renewal", "sub_1", "tran_renewal", "paid", "USD", new BigDecimal("19.99"));
        service.enqueue(subscription, first);
        when(outbox.existsByProviderAndPaymentId(PaymentProviderType.CREEM, "tran_first")).thenReturn(true);
        service.enqueue(subscription, first);
        service.enqueue(subscription, renewal);

        var saved = ArgumentCaptor.forClass(PaymentNotificationOutbox.class);
        verify(outbox, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(PaymentNotificationOutbox::getPaymentId)
                .containsExactly("tran_first", "tran_renewal");
        assertThat(saved.getAllValues().getFirst().getAmountValue()).isEqualByComparingTo("6.50");
        assertThat(saved.getAllValues().getLast().getAmountValue()).isEqualByComparingTo("19.99");
        assertThat(saved.getAllValues()).allSatisfy(n -> {
            assertThat(n.getProvider()).isEqualTo(PaymentProviderType.CREEM);
            assertThat(n.getCurrency()).isEqualTo("USD");
        });
    }

    @Test
    void creemUpgradeSnapshotsAdjustmentAndNewMonthlyPriceOnlyAfterConfirmation() {
        var store = new Store(); store.setStoreId(123L); store.setStoreName("Minha loja");
        var subscription = creemSubscription();
        service.enqueueUpgrade(store, subscription, PlanType.PREMIUM,
                new GatewayInvoice("tran_upgrade", "sub_1", "tran_upgrade", "waiting", "USD", new BigDecimal("3.25")));
        verifyNoInteractions(outbox);
        var invoice = new GatewayInvoice("tran_upgrade", "sub_1", "tran_upgrade", "paid", "USD", new BigDecimal("3.25"));
        service.enqueueUpgrade(store, subscription, PlanType.PREMIUM, invoice);
        when(outbox.existsByProviderAndPaymentId(PaymentProviderType.CREEM, "tran_upgrade")).thenReturn(true);
        service.enqueueUpgrade(store, subscription, PlanType.PREMIUM, invoice);
        var saved = ArgumentCaptor.forClass(PaymentNotificationOutbox.class);
        verify(outbox).save(saved.capture());
        var n = saved.getValue();
        assertThat(n.getEventType()).isEqualTo(PaymentNotificationOutbox.EventType.UPGRADE);
        assertThat(n.getSourcePlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(n.getPlan()).isEqualTo(PlanType.PREMIUM_PLUS);
        assertThat(n.getAmountValue()).isEqualByComparingTo("3.25");
        assertThat(n.getRecurringAmount()).isEqualByComparingTo("19.99");
        assertThat(n.getStoreName()).isEqualTo("Minha loja");
        assertThat(n.getSubscriptionId()).isEqualTo("sub_1");
        assertThat(n.getChargeId()).isEqualTo("tran_upgrade");
    }

    private PaymentSubscription creemSubscription() {
        var subscription = new PaymentSubscription();
        subscription.setStoreId(123L); subscription.setProvider(PaymentProviderType.CREEM);
        subscription.setProviderSubscriptionId("sub_1"); subscription.setPlan(PlanType.PREMIUM_PLUS);
        subscription.setCurrency("USD"); subscription.setAmountValue(new BigDecimal("19.99"));
        return subscription;
    }
    @Test
    void queuesOnlyCompletedUpgradeAndSnapshotsItsActualAdjustmentAmount() {
        var store=new Store();store.setStoreId(7744400L);store.setStoreName("Dark Hunter");
        var a=new UpgradeAdjustment();a.setSourcePlan(PlanType.PREMIUM_PLUS);a.setTargetPlan(PlanType.PREMIUM_ULTRA);
        a.setDueAmount(new BigDecimal("8.97"));a.setRegularAmount(new BigDecimal("59.90"));a.setCouponCode("DARK");a.setSubscriptionId("123");
        service.enqueueUpgrade(store,a);verifyNoInteractions(outbox);
        a.setState(UpgradeAdjustment.State.COMPLETED);service.enqueueUpgrade(store,a);
        var saved=ArgumentCaptor.forClass(PaymentNotificationOutbox.class);verify(outbox).save(saved.capture());
        assertThat(saved.getValue().getStoreName()).isEqualTo("Dark Hunter");
        assertThat(saved.getValue().getAmountValue()).isEqualByComparingTo("8.97");
        assertThat(saved.getValue().getRecurringAmount()).isEqualByComparingTo("59.90");
        assertThat(saved.getValue().getEventType()).isEqualTo(PaymentNotificationOutbox.EventType.UPGRADE);
        when(outbox.existsByProviderAndPaymentId(PaymentProviderType.EFI,a.reference())).thenReturn(true);
        service.enqueueUpgrade(store,a);verify(outbox,times(1)).save(any());
    }
}
