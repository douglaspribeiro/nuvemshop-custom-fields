package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.PaymentEnvironment;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EfiScheduledPixGatewayTest {
    @Test void consentAloneIsPendingAndConfirmedDebitPaysOneMonth() {
        var client=mock(EfiRecurringPixClient.class);
        var db=mock(JdbcTemplate.class);
        var gateway=new EfiScheduledPixGateway(client,new EfiRecurringPixClient.Settings(true,"id","secret","cert","key","1234567",true),db,mock(EfiGateway.class));
        LocalDate today=LocalDate.now(EfiScheduledPixGateway.ZONE);
        when(db.queryForMap(anyString(),eq("reference"),eq(PaymentEnvironment.SANDBOX.name()))).thenReturn(
                Map.of("payment_id","urn:efi:test","amount",BigDecimal.TEN,"first_debit",java.sql.Date.valueOf(today)));
        var json=new ObjectMapper().createArrayNode();
        var contract=json.addObject().put("idProprio","reference").put("identificadorPagamento","urn:efi:test")
                .put("valor","10.00").put("status","ativa");
        var debit=contract.putArray("recorrencias").addObject().put("endToEndId","payment")
                .put("dataOperacao",today.toString()).put("status","pendente");
        when(client.list(any(),any())).thenReturn(json);
        assertThat(gateway.getSubscription("reference").status()).isEqualTo("authorized");
        debit.put("status","aceito");
        var paid=gateway.getSubscription("reference");
        assertThat(paid.status()).isEqualTo("active");
        assertThat(paid.nextPaymentAt()).isEqualTo(today.plusMonths(1).atStartOfDay(EfiScheduledPixGateway.ZONE).toInstant());
        contract.put("valor","0.01");
        assertThatThrownBy(()->gateway.getSubscription("reference")).isInstanceOf(PaymentGatewayException.class);
    }
}
