package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.properties.PaddleProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaddleGatewayTest {
    private static final String SECRET="pdl_ntfset_secret";

    @Test void createsRecurringCatalogTransactionWithInternalReference() {
        PaymentCatalogPriceRepository catalog=mock(PaymentCatalogPriceRepository.class);
        PaymentCatalogPrice price=price("MX","MXN","99.00","pri_mx");
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(
                PaymentProviderType.PADDLE,PaymentEnvironment.SANDBOX,"MX",PlanType.PREMIUM)).thenReturn(Optional.of(price));
        RestClient.Builder builder=RestClient.builder();
        MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://sandbox-api.paddle.test/transactions")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization","Bearer api-key")).andExpect(header("Paddle-Version","1"))
                .andExpect(jsonPath("$.items[0].price_id").value("pri_mx"))
                .andExpect(jsonPath("$.custom_data.external_reference").value("ncf_123_ref"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"txn_1\",\"status\":\"draft\",\"checkout\":{\"url\":\"https://checkout.test\"}}}",MediaType.APPLICATION_JSON));
        Store store=new Store(); store.setStoreId(123L); store.setStoreCountryCode("MX");
        GatewayCheckout result=gateway(builder,catalog).createCheckout(store,PlanType.PREMIUM,"ncf_123_ref","https://unused");
        assertThat(result.checkoutResourceId()).isEqualTo("txn_1");
        server.verify();
    }

    @Test void convertsMinorUnitsForTwoAndZeroDecimalCurrencies() {
        RestClient.Builder builder=RestClient.builder(); MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://sandbox-api.paddle.test/transactions/txn_mx")).andRespond(withSuccess(transaction("MXN","9900"),MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://sandbox-api.paddle.test/transactions/txn_cl")).andRespond(withSuccess(transaction("CLP","4199"),MediaType.APPLICATION_JSON));
        PaddleGateway gateway=gateway(builder,mock(PaymentCatalogPriceRepository.class));
        assertThat(gateway.getTransaction("txn_mx").amount()).isEqualByComparingTo("99.00");
        assertThat(gateway.getTransaction("txn_cl").amount()).isEqualByComparingTo("4199");
        server.verify();
    }

    @Test void verifiesRawWebhookAndRejectsTampering() throws Exception {
        PaddleGateway gateway=gateway(RestClient.builder(),mock(PaymentCatalogPriceRepository.class));
        String body="{\"event_id\":\"evt_1\",\"notification_id\":\"ntf_1\",\"event_type\":\"transaction.completed\",\"occurred_at\":\"2026-09-26T04:00:00Z\",\"data\":{\"id\":\"txn_1\"}}";
        long timestamp=Instant.now().getEpochSecond();
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String digest=HexFormat.of().formatHex(mac.doFinal((timestamp+":"+body).getBytes(StandardCharsets.UTF_8)));
        GatewayNotification notification=gateway.verifyNotification(body,"ts="+timestamp+";h1="+digest,null,null);
        assertThat(notification.eventKey()).isEqualTo("PADDLE:evt_1");
        assertThat(notification.resourceId()).isEqualTo("txn_1");
        assertThatThrownBy(()->gateway.verifyNotification(body+" ","ts="+timestamp+";h1="+digest,null,null))
                .isInstanceOf(PaymentGatewayException.class);
    }

    private static PaddleGateway gateway(RestClient.Builder builder, PaymentCatalogPriceRepository catalog) {
        PaddleProperties properties=new PaddleProperties(true,true,"https://sandbox-api.paddle.test","api-key","test_token",SECRET,"1",3,300,30);
        return new PaddleGateway(properties,catalog,builder,new ObjectMapper());
    }
    private static PaymentCatalogPrice price(String country,String currency,String amount,String id) {
        PaymentCatalogPrice price=new PaymentCatalogPrice(); price.setProvider(PaymentProviderType.PADDLE);
        price.setEnvironment(PaymentEnvironment.SANDBOX); price.setCountryCode(country); price.setPlan(PlanType.PREMIUM);
        price.setCurrency(currency); price.setAmountValue(new BigDecimal(amount)); price.setProviderPriceId(id);
        price.setTaxMode("internal"); price.setRecurring(true); price.setEnabled(true); return price;
    }
    private static String transaction(String currency,String total) {
        return "{\"data\":{\"id\":\"txn_1\",\"status\":\"completed\",\"subscription_id\":null,"+
                "\"customer_id\":\"ctm_1\",\"currency_code\":\""+currency+"\",\"custom_data\":{\"external_reference\":\"ncf_ref\"},"+
                "\"details\":{\"totals\":{\"total\":\""+total+"\"}}}}";
    }
}
