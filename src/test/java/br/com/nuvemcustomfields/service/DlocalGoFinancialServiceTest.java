package br.com.nuvemcustomfields.service;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DlocalGoFinancialServiceTest {
    final DlocalGoGateway gateway=mock(DlocalGoGateway.class);
    final DlocalGoReversalRepository repository=mock(DlocalGoReversalRepository.class);
    final Map<String,DlocalGoReversal> ledger=new HashMap<>();
    final DlocalGoFinancialService service=new DlocalGoFinancialService(gateway,repository);
    DlocalGoFinancialServiceTest() {
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(repository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(ledger.get(i.getArgument(0))));
        when(repository.save(any())).thenAnswer(i -> {DlocalGoReversal e=i.getArgument(0);ledger.put(e.getId(),e);return e;});
        when(repository.findByEnvironmentAndOrderId(any(),anyString())).thenAnswer(i -> ledger.values().stream()
                .filter(e -> e.getEnvironment()==i.getArgument(0) && e.getOrderId().equals(i.getArgument(1))).toList());
    }
    JsonNode json(String s) throws Exception{return new ObjectMapper().readTree(s);}
    @Test void fullRefundRemainsRevokedWhenProviderLaterReturnsAStaleEmptyPage() throws Exception {
        when(gateway.financialPages("/v1/refunds?country=AR")).thenReturn(List.of(json("""
            {"id":"REF-1","order_id":"ST-1","currency":"ARS","amount":100,"status":"SUCCESS"}
            """)),List.of());
        assertThat(service.paymentStatus("ST-1","ARS",new BigDecimal("100"))).isEqualTo("refunded");
        assertThat(service.paymentStatus("ST-1","ARS",new BigDecimal("100"))).isEqualTo("refunded");
        assertThat(service.paymentStatus("ST-NEW","ARS",new BigDecimal("100"))).isEqualTo("paid");
    }
    @Test void partialRefundPreservesAccessAndIgnoresAnotherOrder() throws Exception {
        when(gateway.financialPages("/v1/refunds?country=AR")).thenReturn(List.of(json("""
            {"id":"REF-1","order_id":"ST-1","currency":"ARS","amount":20,"status":"SUCCESS"}
            """),json("""
            {"id":"REF-2","order_id":"ST-OTHER","currency":"USD","amount":999,"status":"SUCCESS"}
            """)));
        assertThat(service.paymentStatus("ST-1","ARS",new BigDecimal("100"))).isEqualTo("partialRefund");
    }
    @Test void chargebackBlocksWhileOpenAndCanRecoverAfterExplicitCancellation() throws Exception {
        when(gateway.financialPages("/v1/chargebacks?country=AR")).thenReturn(List.of(json("""
            {"id":"CBK-1","order_id":"ST-1","currency":"ARS","local_amount":100,"status":"PENDING"}
            """)),List.of(json("""
            {"id":"CBK-1","order_id":"ST-1","currency":"ARS","local_amount":100,"status":"CANCELLED"}
            """)));
        assertThat(service.paymentStatus("ST-1","ARS",new BigDecimal("100"))).isEqualTo("chargedBack");
        assertThat(service.paymentStatus("ST-1","ARS",new BigDecimal("100"))).isEqualTo("paid");
    }
    @Test void mismatchedCurrencyFailsBeforePersistingOrGrantingAccess() throws Exception {
        when(gateway.financialPages("/v1/refunds?country=AR")).thenReturn(List.of(json("""
            {"id":"REF-1","order_id":"ST-1","currency":"USD","amount":100,"status":"SUCCESS"}
            """)));
        assertThatThrownBy(() -> service.paymentStatus("ST-1","ARS",new BigDecimal("100"))).hasMessageContaining("inválido");
        verify(repository,never()).save(any());
    }
}
