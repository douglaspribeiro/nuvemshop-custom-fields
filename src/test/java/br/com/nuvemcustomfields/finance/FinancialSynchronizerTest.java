package br.com.nuvemcustomfields.finance;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialSynchronizerTest {
    @Test void eachPaidRenewalIsRecordedAndReadFailuresPreserveKnownDataWithoutBillingWrites()throws Exception{
        var gateway=mock(PaymentGateway.class);var subscriptions=mock(PaymentSubscriptionRepository.class);var ledger=mock(FinancialLedger.class);
        when(gateway.provider()).thenReturn(PaymentProviderType.EFI);when(gateway.configured()).thenReturn(true);when(gateway.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
        var local=new PaymentSubscription();local.setStoreId(1L);local.setProvider(PaymentProviderType.EFI);local.setProviderEnvironment(PaymentEnvironment.PRODUCTION);local.setProviderSubscriptionId("sub1");local.setCountryCode("BR");
        when(subscriptions.findAll()).thenReturn(List.of(local));when(ledger.entries()).thenReturn(List.of());
        when(ledger.record(any(),anyString())).thenAnswer(inv->{FinancialEntry e=inv.getArgument(0);return e.withKey(FinancialLedger.hash(e.identity()));});
        var mapper=new ObjectMapper();when(gateway.financialHistory("sub1")).thenReturn(List.of(mapper.readTree("{\"charge_id\":1,\"status\":\"paid\",\"paid_value\":1999}"),mapper.readTree("{\"charge_id\":2,\"status\":\"paid\",\"paid_value\":1999}")));
        var sync=new FinancialSynchronizer(List.of(gateway),subscriptions,ledger,new FinancialNormalizer(),new MockEnvironment().withProperty("payments.finance.accounts.efi","merchant_a"));
        sync.synchronize();verify(ledger,times(4)).record(argThat(e->e.account().equals("merchant_a")),eq("gateway-sync"));
        when(gateway.financialHistory("sub1")).thenThrow(new PaymentGatewayException("Consulta indisponível"));sync.synchronize();
        verify(ledger,atLeastOnce()).syncStatus(eq("EFI:merchant_a:PRODUCTION:sub1"),eq(PaymentProviderType.EFI),eq("merchant_a"),eq(PaymentEnvironment.PRODUCTION),anyString(),eq(false));
        verify(gateway,never()).cancel(anyString());verify(gateway,never()).cancelImmediately(anyString());verify(subscriptions,never()).save(any());
    }
    @Test void missingAccountCredentialsAreVisibleWithoutInventingAnEmptySuccessfulBalance(){
        var gateway=mock(PaymentGateway.class);var subscriptions=mock(PaymentSubscriptionRepository.class);var ledger=mock(FinancialLedger.class);
        when(subscriptions.findAll()).thenReturn(List.of());when(gateway.provider()).thenReturn(PaymentProviderType.EFI);when(gateway.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
        new FinancialSynchronizer(List.of(gateway),subscriptions,ledger,new FinancialNormalizer(),new MockEnvironment()).synchronize();
        verify(ledger).syncStatus(eq("EFI:default:PRODUCTION"),eq(PaymentProviderType.EFI),eq("default"),eq(PaymentEnvironment.PRODUCTION),contains("sem credenciais"),eq(false));
        verify(ledger,never()).record(any(),anyString());
    }
}
