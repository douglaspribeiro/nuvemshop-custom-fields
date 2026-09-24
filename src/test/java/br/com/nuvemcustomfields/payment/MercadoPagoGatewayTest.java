package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.MercadoPagoProperties;
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
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MercadoPagoGatewayTest {
    private static final String SECRET = "webhook-secret";

    @Test
    void createsPendingCreditCardSubscriptionWithIdempotency() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.example.com/preapproval"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andExpect(header("X-Idempotency-Key", "ncf_123_ref"))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.payer_email").value("owner@example.com"))
                .andExpect(jsonPath("$.auto_recurring.currency_id").value("BRL"))
                .andExpect(jsonPath("$.payment_methods_allowed.payment_types[0].id").value("credit_card"))
                .andRespond(withSuccess("{\"id\":\"sub-1\",\"init_point\":\"https://mp.test/checkout\",\"status\":\"pending\"}",
                        MediaType.APPLICATION_JSON));

        Store store = new Store();
        store.setStoreId(123L);
        store.setStoreCountryCode("BR");
        store.setStoreCurrency("BRL");
        store.setStoreEmail("OWNER@EXAMPLE.COM");
        MercadoPagoGateway gateway = gateway(builder);

        GatewayCheckout checkout = gateway.createCheckout(store, PlanType.PREMIUM, "ncf_123_ref", "https://app.test/return");

        assertThat(checkout.subscriptionId()).isEqualTo("sub-1");
        assertThat(checkout.checkoutUrl()).isEqualTo("https://mp.test/checkout");
        server.verify();
    }

    @Test
    void validatesOfficialWebhookManifestAndRejectsTampering() throws Exception {
        MercadoPagoGateway gateway = gateway(RestClient.builder());
        String dataId = "SUB-123";
        String requestId = "req-456";
        String timestamp = "1758660000";
        String manifest = "id:sub-123;request-id:req-456;ts:1758660000;";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String digest = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        String body = "{\"id\":99,\"type\":\"subscription_preapproval\",\"action\":\"updated\",\"data\":{\"id\":\"SUB-123\"}}";

        GatewayNotification notification = gateway.verifyNotification(body, "ts=" + timestamp + ",v1=" + digest, requestId, dataId);

        assertThat(notification.resourceId()).isEqualTo(dataId);
        assertThat(notification.type()).isEqualTo("subscription_preapproval");
        assertThatThrownBy(() -> gateway.verifyNotification(body, "ts=" + timestamp + ",v1=bad", requestId, dataId))
                .isInstanceOf(PaymentGatewayException.class);
    }

    private MercadoPagoGateway gateway(RestClient.Builder builder) {
        return new MercadoPagoGateway(new MercadoPagoProperties(true, "https://api.example.com", "access-token",
                SECRET, 3, new BigDecimal("19.99"), new BigDecimal("29.99")), builder, new ObjectMapper());
    }
}
