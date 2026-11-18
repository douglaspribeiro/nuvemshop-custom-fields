package br.com.nuvemcustomfields.finance;
import br.com.nuvemcustomfields.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
class FinancialNormalizerTest {
    final FinancialNormalizer parser=new FinancialNormalizer();final ObjectMapper mapper=new ObjectMapper();
    @Test void mercadoPagoKeepsProviderCalendarDateAndReleasePendingEvenIfDatePassed()throws Exception{
        var data=mapper.readTree("""
            {"id":123,"status":"approved","live_mode":true,"currency_id":"BRL","transaction_amount":20,
            "date_approved":"2026-11-01T23:30:00-03:00","money_release_date":"2026-11-03T23:30:00-03:00","money_release_status":"pending",
            "transaction_details":{"net_received_amount":18.50},"fee_details":[{"amount":1.5}]}
            """);
        var rows=parser.payment(PaymentProviderType.MERCADO_PAGO,"account",PaymentEnvironment.PRODUCTION,1L,"sub","BR",data);
        assertThat(rows).hasSize(2);assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026,11,1));
        assertThat(rows.get(1).date()).isEqualTo(LocalDate.of(2026,11,3));assertThat(rows.get(1).certainty()).isEqualTo(FinancialEntry.Certainty.PROVIDER);
        assertThat(rows.get(1).net()).isEqualByComparingTo("18.50");
    }
    @Test void dodoCreationAndUpdateNeverInventPaymentDateButLedgerRevealsSettlement()throws Exception{
        var data=mapper.readTree("""
            {"payment_id":"pay1","status":"succeeded","currency":"USD","total_amount":499,"tax":0,
            "created_at":"2026-11-16T21:51:40Z","updated_at":"2026-11-17T15:06:42Z","settlement_currency":"USD","settlement_amount":499,
            "financial_ledger":[{"id":"l1","event_type":"payment","amount":499,"currency":"USD","is_credit":true,"created_at":"2026-11-16T21:54:40Z"},
            {"id":"l2","event_type":"payment_fees","amount":71,"currency":"USD","is_credit":false,"created_at":"2026-11-16T21:54:40Z"}]}
            """);
        var rows=parser.payment(PaymentProviderType.DODO,"default",PaymentEnvironment.PRODUCTION,1L,"sub","MX",data);
        assertThat(rows.get(0).paidDate()).isNull();assertThat(rows.get(0).paidAt()).isNull();
        assertThat(rows.get(1).net()).isEqualByComparingTo("4.28");assertThat(rows.get(1).date()).isNull();
        assertThat(rows.get(1).status()).isEqualTo("ledger_posted");assertThat(rows.get(1).certainty()).isEqualTo(FinancialEntry.Certainty.PROVIDER);
    }
    @Test void stripeAvailableIsGatewayBalanceAndCurrencyConversionDoesNotUseChargeCurrency()throws Exception{
        var data=mapper.readTree("""
            {"id":"ch1","status":"succeeded","paid":true,"livemode":true,"currency":"ars","amount":100000,"created":1794873600,
            "balance_transaction":{"id":"txn1","currency":"usd","amount":100,"fee":5,"net":95,"available_on":1794873600,"status":"available"}}
            """);
        var rows=parser.payment(PaymentProviderType.STRIPE,"default",PaymentEnvironment.PRODUCTION,1L,"sub","AR",data);
        assertThat(rows.get(0).currency()).isEqualTo("ARS");assertThat(rows.get(1).currency()).isEqualTo("USD");
        assertThat(rows.get(1).net()).isEqualByComparingTo("0.95");assertThat(rows).noneMatch(e->e.stage()==FinancialEntry.Stage.BANK);
    }
    @Test void sandboxMismatchAndUnpaidDoNotCreateRealMoney()throws Exception{
        assertThat(parser.payment(PaymentProviderType.EFI,"default",PaymentEnvironment.PRODUCTION,1L,"s","BR",mapper.readTree("{\"status\":\"settled\"}"))).isEmpty();
        assertThatThrownBy(()->parser.payment(PaymentProviderType.STRIPE,"default",PaymentEnvironment.PRODUCTION,1L,"s","AR",mapper.readTree("{\"id\":\"c\",\"status\":\"succeeded\",\"paid\":true,\"livemode\":false,\"currency\":\"usd\",\"amount\":100}"))).isInstanceOf(IllegalArgumentException.class);
    }
}
