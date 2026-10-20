package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.properties.DodoTestProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DodoTestGatewayTest {
    final PaymentCatalogPriceRepository catalog = mock(PaymentCatalogPriceRepository.class);
    final RestClient.Builder builder = RestClient.builder();
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final String secret = "whsec_" + Base64.getEncoder().encodeToString("a-test-signing-key-with-enough-length".getBytes(StandardCharsets.UTF_8));
    DodoTestGateway gateway;
    @BeforeEach void setup() {
        gateway = new DodoTestGateway(new DodoTestProperties(true, "dodo_test_credential", secret, "42", 20), catalog, builder.build(), new ObjectMapper());
    }
    @AfterEach void close() { gateway.close(); }
    Store store(long id, String country) { var store = new Store(); store.setStoreId(id); store.setStoreCountryCode(country); return store; }
    PaymentCatalogPrice price() {
        var price = new PaymentCatalogPrice(); price.setProvider(PaymentProviderType.DODO_TEST); price.setEnvironment(PaymentEnvironment.SANDBOX);
        price.setCountryCode("CO"); price.setPlan(PlanType.PREMIUM); price.setCurrency("USD"); price.setAmountValue(new BigDecimal("1.00"));
        price.setProviderPriceId("pdt_test"); price.setEnabled(true); price.setValidatedAt(Instant.now()); return price;
    }
    String product() { return """
        {"product_id":"pdt_test","is_recurring":true,"tax_category":"saas","price":{"type":"recurring_price","price":100,"currency":"USD","payment_frequency_count":1,"payment_frequency_interval":"Month"}}
        """; }
    @Test void onlyAuthorizedForeignStoreAndConfiguredPlanCanUseTestGateway() {
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.DODO_TEST,PaymentEnvironment.SANDBOX,"CO",PlanType.PREMIUM)).thenReturn(Optional.of(price()));
        assertThat(gateway.supports(store(42,"CO"))).isTrue();
        assertThat(gateway.supports(store(43,"CO"))).isFalse(); assertThat(gateway.supports(store(42,"BR"))).isFalse();
        assertThat(gateway.planAvailable(store(42,"CO"),PlanType.PREMIUM_PLUS)).isFalse();
        assertThat(new DodoTestProperties(true,"opaque-test-api-key",secret,"42",20).configured()).isTrue();
        assertThat(new DodoTestProperties(true,"dodo_test_credential",secret,"0",20).allows(42L)).isTrue();
    }
    @Test void allStoresSettingStillExcludesBrazilAndMissingCatalog() {
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.DODO_TEST,PaymentEnvironment.SANDBOX,"CO",PlanType.PREMIUM)).thenReturn(Optional.of(price()));
        var all = new DodoTestGateway(new DodoTestProperties(true,"dodo_test_credential",secret,"0",20),catalog,builder.build(),new ObjectMapper());
        try {
            assertThat(all.supports(store(43,"CO"))).isTrue();
            assertThat(all.supports(store(43,"BR"))).isFalse();
            assertThat(all.supports(store(43,"CL"))).isFalse();
        } finally { all.close(); }
    }
    @Test void createsHostedTestCheckoutWithFixedCurrencyAndStoreReference() {
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.DODO_TEST,PaymentEnvironment.SANDBOX,"CO",PlanType.PREMIUM)).thenReturn(Optional.of(price()));
        server.expect(requestTo("https://test.dodopayments.com/products/pdt_test")).andRespond(withSuccess(product(),MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://test.dodopayments.com/checkouts")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization","Bearer dodo_test_credential"))
                .andExpect(jsonPath("$.product_cart[0].product_id").value("pdt_test"))
                .andExpect(jsonPath("$.metadata.external_reference").value("ncf_ref"))
                .andExpect(jsonPath("$.metadata.store_id").value("42"))
                .andExpect(jsonPath("$.billing_currency").value("USD"))
                .andExpect(jsonPath("$.feature_flags.allow_currency_selection").value(false))
                .andRespond(withSuccess("{\"session_id\":\"cks_test\",\"checkout_url\":\"https://test.checkout.dodopayments.com/session\"}",MediaType.APPLICATION_JSON));
        assertThat(gateway.createCheckout(store(42,"CO"),PlanType.PREMIUM,"ncf_ref","https://app.test/return").checkoutResourceId()).isEqualTo("cks_test"); server.verify();
    }
    @Test void rejectsUnexpectedProductPriceAndProductionCatalog() {
        var price = price(); price.setAmountValue(BigDecimal.TEN);
        server.expect(requestTo("https://test.dodopayments.com/products/pdt_test")).andRespond(withSuccess(product(),MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.validateCatalog(price)).isInstanceOf(IllegalArgumentException.class);
        price.setEnvironment(PaymentEnvironment.PRODUCTION);
        assertThatThrownBy(() -> gateway.validateCatalog(price)).isInstanceOf(IllegalArgumentException.class); server.verify();
    }
    @Test void verifiesSignatureAndTimestampUsingOfficialSdk() throws Exception {
        String body = "{\"business_id\":\"bus_test\",\"type\":\"payment.succeeded\",\"timestamp\":\"2026-10-15T12:00:00Z\",\"data\":{\"payload_type\":\"Payment\",\"payment_id\":\"pay_test\"}}";
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = sign("evt_test",timestamp,body);
        assertThat(gateway.verifyNotification(body,signature,"evt_test",timestamp).eventKey()).isEqualTo("DODO_TEST:SANDBOX:evt_test");
        assertThatThrownBy(() -> gateway.verifyNotification(body+" ",signature,"evt_test",timestamp)).isInstanceOf(PaymentGatewayException.class);
        String old = Long.toString(Instant.now().minusSeconds(600).getEpochSecond());
        assertThatThrownBy(() -> gateway.verifyNotification(body,sign("evt_test",old,body),"evt_test",old)).isInstanceOf(PaymentGatewayException.class);
        assertThatThrownBy(() -> gateway.verifyNotification(body,null,"evt_test",timestamp)).isInstanceOf(PaymentGatewayException.class);
    }
    String sign(String id,String timestamp,String body) throws Exception {
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret.substring(6)),"HmacSHA256"));
        return "v1,"+Base64.getEncoder().encodeToString(mac.doFinal((id+"."+timestamp+"."+body).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void mapsPaidInvoicesAndScheduledCancellation() {
        server.expect(requestTo("https://test.dodopayments.com/subscriptions/sub_test"))
                .andRespond(withSuccess("{\"subscription_id\":\"sub_test\",\"status\":\"active\",\"currency\":\"USD\",\"recurring_pre_tax_amount\":100,\"product_id\":\"pdt_test\",\"next_billing_date\":\"2026-11-15T12:00:00Z\",\"cancel_at_next_billing_date\":true,\"metadata\":{\"external_reference\":\"ncf_ref\"}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://test.dodopayments.com/payments/pay_test"))
                .andRespond(withSuccess("{\"payment_id\":\"pay_test\",\"subscription_id\":\"sub_test\",\"status\":\"succeeded\",\"currency\":\"USD\",\"total_amount\":110}",MediaType.APPLICATION_JSON));
        assertThat(gateway.getSubscription("sub_test").cancellationEffectiveAt()).isEqualTo(Instant.parse("2026-11-15T12:00:00Z"));
        var invoice=gateway.getInvoice("pay_test");assertThat(invoice.approved()).isTrue();assertThat(invoice.amountPaid()).isEqualByComparingTo("1.10");server.verify();
    }
}
