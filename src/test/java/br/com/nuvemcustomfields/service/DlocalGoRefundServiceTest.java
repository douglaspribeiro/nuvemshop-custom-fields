package br.com.nuvemcustomfields.service;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.entity.DlocalGoOperation.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.properties.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DlocalGoRefundServiceTest {
    final DlocalGoGateway gateway=mock(DlocalGoGateway.class);
    final DlocalGoFinancialService finances=mock(DlocalGoFinancialService.class);
    final DlocalGoOperationRepository operations=mock(DlocalGoOperationRepository.class);
    final DlocalGoReversalRepository reversals=mock(DlocalGoReversalRepository.class);
    final PaymentSubscriptionRepository subscriptions=mock(PaymentSubscriptionRepository.class);
    final StoreRepository stores=mock(StoreRepository.class);
    final PaymentSubscriptionService payments=mock(PaymentSubscriptionService.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final Map<String,DlocalGoOperation> saved=new LinkedHashMap<>();
    final PaymentSubscription sub=new PaymentSubscription();
    DlocalGoRefundService service;
    @BeforeEach void setup() throws Exception {
        sub.setStoreId(42L);sub.setProvider(PaymentProviderType.DLOCAL_GO);sub.setProviderEnvironment(PaymentEnvironment.SANDBOX);
        sub.setProviderSubscriptionId("20:3");sub.setExternalReference("ncf_ref");sub.setCurrency("ARS");sub.setAccessActive(true);
        when(subscriptions.findByStoreId(42L)).thenReturn(Optional.of(sub));when(stores.findByStoreIdForUpdate(42L)).thenReturn(Optional.of(new Store()));
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(gateway.getInvoice("20:3:1")).thenReturn(new GatewayInvoice("20:3:1","20:3","ST-1","paid","ARS",new BigDecimal("100")));
        when(gateway.paymentForOrder("ST-1")).thenReturn(new ObjectMapper().readTree("{\"id\":\"DP-1\",\"currency\":\"ARS\",\"amount\":100}"));
        when(finances.refunded(anyString())).thenReturn(BigDecimal.ZERO);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(operations.save(any())).thenAnswer(i -> {DlocalGoOperation op=i.getArgument(0);saved.put(op.getId(),op);return op;});
        when(operations.findById(anyString())).thenAnswer(i -> Optional.ofNullable(saved.get(i.getArgument(0))));
        when(operations.lockById(anyString())).thenAnswer(i -> Optional.ofNullable(saved.get(i.getArgument(0))));
        when(operations.findByStoreIdOrderByCreatedAtDesc(42L)).thenAnswer(i -> new ArrayList<>(saved.values()));
        var props=mock(NuvemshopProperties.class);when(props.appBaseUrl()).thenReturn("https://app.test");
        @SuppressWarnings("unchecked") ObjectProvider<PaymentSubscriptionService> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(payments);
        service=new DlocalGoRefundService(gateway,finances,operations,reversals,subscriptions,stores,provider,props,manager);
    }
    @Test void rejectsOtherProvidersAndCurrenciesWithoutSendingAnything() {
        sub.setProvider(PaymentProviderType.CREEM);sub.setCurrency("USD");
        assertThatThrownBy(() -> service.quote(42L,"20:3:1",BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway);verifyNoInteractions(operations);
    }
    @Test void totalRefundIsReservedBeforeHttpAndDuplicateFormDoesNotRefundTwice() throws Exception {
        var op=service.quote(42L,"20:3:1",new BigDecimal("100"));
        when(gateway.refund(eq("DP-1"),eq(new BigDecimal("100")),anyString())).thenAnswer(i -> {
            assertThat(op.getState()).isEqualTo(State.CREATING);verify(manager,atLeastOnce()).commit(any());
            return new ObjectMapper().readTree("{\"id\":\"REF-1\",\"amount\":100,\"status\":\"PENDING\"}");
        });
        when(gateway.refundDetail("REF-1")).thenReturn(new ObjectMapper().readTree("{\"id\":\"REF-1\",\"order_id\":\"ST-1\",\"currency\":\"ARS\",\"amount\":100,\"status\":\"SUCCESS\"}"));
        service.execute(op.getId());service.execute(op.getId());
        verify(gateway,times(1)).refund(any(),any(),any());verify(reversals).saveAndFlush(any());verify(payments).reconcile(42L);
        assertThat(op.getState()).isEqualTo(State.COMPLETED);
    }
    @Test void timeoutPreservesReservationAndNeverResendsRefund() {
        var op=service.quote(42L,"20:3:1",BigDecimal.TEN);
        when(gateway.refund(any(),any(),any())).thenThrow(new PaymentGatewayException("timeout"));
        assertThatThrownBy(() -> service.execute(op.getId())).isInstanceOf(PaymentGatewayException.class);
        assertThat(op.getState()).isEqualTo(State.CREATING);service.execute(op.getId());
        verify(gateway,times(1)).refund(any(),any(),any());assertThat(sub.isAccessActive()).isTrue();
    }
    @Test void successfulAndPendingRefundsCannotExceedOriginalPayment() {
        when(finances.refunded("ST-1")).thenReturn(new BigDecimal("90"));
        assertThatThrownBy(() -> service.quote(42L,"20:3:1",new BigDecimal("11"))).hasMessageContaining("saldo");
        verify(gateway,never()).refund(any(),any(),any());
    }
    @Test void anotherSubscriptionsInvoiceCannotBeRefunded() {
        when(gateway.getInvoice("20:3:1")).thenReturn(new GatewayInvoice("20:3:1","99:3","ST-1","paid","ARS",new BigDecimal("100")));
        assertThatThrownBy(() -> service.quote(42L,"20:3:1",BigDecimal.TEN)).hasMessageContaining("não pertence");
        verify(gateway,never()).paymentForOrder(any());
    }
}
