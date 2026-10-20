package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.properties.StripeProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

class StripeGatewayTest {
    final PaymentCatalogPriceRepository catalog = mock(PaymentCatalogPriceRepository.class);
    final RestClient.Builder builder = RestClient.builder();
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final ObjectMapper mapper = new ObjectMapper();
    StripeGateway gateway(boolean enabled) {
        return new StripeGateway(new StripeProperties(enabled,true,"sk_test_fixture_only","whsec_fixture_only","42",0,20),catalog,builder.build(),mapper);
    }
    PaymentCatalogPrice price() {
        var price = new PaymentCatalogPrice(); price.setProvider(PaymentProviderType.STRIPE); price.setEnvironment(PaymentEnvironment.SANDBOX);
        price.setCountryCode("AR"); price.setCurrency("ARS"); price.setAmountValue(new BigDecimal("5599.00"));
        price.setPlan(PlanType.PREMIUM); price.setProviderPriceId("price_1"); price.setTaxMode("inclusive");
        price.setEnabled(true); price.setValidatedAt(Instant.now()); return price;
    }
    String remotePrice() { return """
        {"id":"price_1","object":"price","livemode":false,"active":true,"currency":"ars","unit_amount":559900,
         "product":{"id":"prod_1","active":true},"type":"recurring","billing_scheme":"per_unit","tax_behavior":"inclusive",
         "recurring":{"interval":"month","interval_count":1,"usage_type":"licensed"}}
        """; }
    void expectPrice(String response) {
        server.expect(requestTo("https://api.stripe.com/v1/prices/price_1?expand%5B%5D=product"))
                .andExpect(header("Authorization","Bearer sk_test_fixture_only"))
                .andExpect(header("Stripe-Version",StripeGateway.API_VERSION)).andRespond(withSuccess(response,MediaType.APPLICATION_JSON));
    }
    @Test void createsProductAndMonthlyDefaultPriceTogetherWithIdempotencyBeforeSalesAreEnabled() {
        var gateway = new StripeGateway(new StripeProperties(false,true,"sk_test_fixture_only","","",0,20),catalog,builder.build(),mapper);
        assertThat(gateway.apiConfigured()).isTrue(); assertThat(gateway.operational()).isFalse();
        server.expect(requestTo("https://api.stripe.com/v1/products")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key","ncf-catalog-stable-key"))
                .andExpect(header("Stripe-Version",StripeGateway.API_VERSION))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("default_price_data%5Bcurrency%5D=ars")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("default_price_data%5Bunit_amount%5D=559900")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("default_price_data%5Brecurring%5D%5Binterval%5D=month")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("default_price_data%5Btax_behavior%5D=inclusive")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("metadata%5Bncf_publication%5D=stable-key")))
                .andRespond(withSuccess("{\"id\":\"prod_1\",\"livemode\":false,\"default_price\":\"price_1\"}",MediaType.APPLICATION_JSON));
        var response=gateway.createProductAndPrice("Essencial","Mensal","ARS",new BigDecimal("5599.00"),PlanType.PREMIUM,"stable-key");
        assertThat(response.path("default_price").asText()).isEqualTo("price_1"); server.verify();
    }
    @Test void createsHostedSubscriptionWithStableIdempotencyAndServerMetadata() {
        var store = new Store(); store.setStoreId(42L); store.setStoreCountryCode("AR");
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(eq(PaymentProviderType.STRIPE),eq(PaymentEnvironment.SANDBOX),eq("AR"),any())).thenReturn(Optional.of(price()));
        expectPrice(remotePrice());
        server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key","ncf-checkout-ncf_ref"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("subscription_data%5Bmetadata%5D%5Bexternal_reference%5D=ncf_ref")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("line_items%5B0%5D%5Bprice%5D=price_1")))
                .andRespond(withSuccess("{\"id\":\"cs_test_1\",\"livemode\":false,\"status\":\"open\",\"url\":\"https://checkout.stripe.com/c/pay/cs_test_1\"}",MediaType.APPLICATION_JSON));
        assertThat(gateway(true).createCheckout(store,PlanType.PREMIUM,"ncf_ref","https://app.test/return").checkoutResourceId()).isEqualTo("cs_test_1"); server.verify();
    }
    @Test void rejectsAnotherEnvironmentCurrencyAmountOrRecurringPeriod() {
        for (String wrong : List.of(remotePrice().replace("false","true"),remotePrice().replace("559900","559901"),
                remotePrice().replace("\"ars\"","\"usd\""),remotePrice().replace("\"month\"","\"year\""))) {
            server.reset(); expectPrice(wrong);
            assertThatThrownBy(() -> gateway(true).validateCatalog(price())).isInstanceOfAny(IllegalArgumentException.class,PaymentGatewayException.class);
            server.verify();
        }
    }
    @Test void sandboxRequiresExplicitStoreAndArgentina() {
        var store = new Store(); store.setStoreId(43L); store.setStoreCountryCode("AR");
        assertThat(gateway(true).supports(store)).isFalse();
        assertThatThrownBy(() -> gateway(true).createCheckout(store,PlanType.PREMIUM,"ref","https://app.test")).isInstanceOf(IllegalArgumentException.class);
        store.setStoreId(42L); store.setStoreCountryCode("BR"); assertThat(gateway(true).supports(store)).isFalse();
        assertThat(new StripeProperties(true,true,"sk_live_fixture","whsec_fixture","42",0,20).configured()).isFalse();
        assertThat(new StripeProperties(true,true,"sk_test_fixture","whsec_fixture","0",0,20).allows(42L)).isFalse();
        verifyNoInteractions(catalog);
    }
    @ParameterizedTest
    @ValueSource(strings = {"2025-06-30.basil", "2026-03-25.dahlia"})
    void signatureUsesRawBodyTimestampAndAllowsRotatedSignatures(String version) throws Exception {
        var gateway = gateway(false); long now = Instant.now().getEpochSecond();
        String body = "{\"id\":\"evt_1\",\"type\":\"customer.subscription.updated\",\"livemode\":false,\"api_version\":\""+version+"\",\"created\":"+now+",\"data\":{\"object\":{\"id\":\"sub_1\"}}}";
        String signature = sign(body,now);
        assertThat(gateway.configured()).isFalse(); assertThat(gateway.operational()).isTrue();
        assertThat(gateway.verifyNotification(body,"v1="+"0".repeat(64)+","+signature,null,null).eventKey()).isEqualTo("STRIPE:SANDBOX:evt_1");
        assertThatThrownBy(() -> gateway.verifyNotification(body+" ",signature,null,null)).isInstanceOf(PaymentGatewayException.class);
        assertThatThrownBy(() -> gateway.verifyNotification(body,sign(body,now-301),null,null)).isInstanceOf(PaymentGatewayException.class);
        String live = body.replace("false","true");
        assertThatThrownBy(() -> gateway.verifyNotification(live,sign(live,now),null,null)).isInstanceOf(PaymentGatewayException.class);
        assertThatThrownBy(() -> gateway.verifyNotification(body,null,null,null)).isInstanceOf(PaymentGatewayException.class);
    }
    @Test void rejectsUntestedVersionsAndThinEventsEvenWithValidSignatures() throws Exception {
        long now = Instant.now().getEpochSecond();
        for (String body : List.of(
                "{\"id\":\"evt_1\",\"api_version\":\"2026-09-30.endive\",\"livemode\":false,\"type\":\"invoice.paid\",\"created\":"+now+",\"data\":{\"object\":{\"id\":\"in_1\"}}}",
                "{\"id\":\"evt_1\",\"api_version\":\"2026-03-25.dahlia\",\"livemode\":false,\"type\":\"invoice.paid\",\"created\":"+now+",\"related_object\":{\"id\":\"in_1\"}}")) {
            assertThatThrownBy(() -> gateway(false).verifyNotification(body,sign(body,now),null,null))
                    .isInstanceOf(PaymentGatewayException.class);
        }
    }
    @Test void readsBasilPeriodsAndDoesNotDependOnRemovedInvoicePaidField() {
        server.expect(requestTo("https://api.stripe.com/v1/subscriptions/sub_1"))
                .andRespond(withSuccess("""
                    {"id":"sub_1","livemode":false,"metadata":{"external_reference":"ncf_ref"},"customer":"cus_1","status":"active",
                     "cancel_at_period_end":true,"items":{"data":[{"id":"si_1","quantity":1,"current_period_start":1790000000,"current_period_end":1890000000,
                      "price":{"id":"price_1","currency":"ars","unit_amount":559900,"recurring":{"interval":"month","interval_count":1,"usage_type":"licensed"}}}]}}
                    """,MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.stripe.com/v1/invoices/in_1"))
                .andRespond(withSuccess("{\"id\":\"in_1\",\"livemode\":false,\"status\":\"paid\",\"currency\":\"ars\",\"amount_paid\":0,\"amount_remaining\":0,\"parent\":{\"subscription_details\":{\"subscription\":\"sub_1\"}}}",MediaType.APPLICATION_JSON));
        var gateway = gateway(false);
        assertThat(gateway.getSubscription("sub_1").cancellationEffectiveAt()).isEqualTo(Instant.ofEpochSecond(1890000000));
        assertThat(gateway.getInvoice("in_1").approved()).isTrue(); server.verify();
    }
    @Test void fullRefundChangesAnOtherwisePaidInvoiceIntoRevokedPayment() {
        server.expect(requestTo("https://api.stripe.com/v1/invoices/in_1"))
                .andRespond(withSuccess("{\"id\":\"in_1\",\"livemode\":false,\"status\":\"paid\",\"currency\":\"ars\",\"amount_paid\":559900,\"amount_remaining\":0,\"parent\":{\"subscription_details\":{\"subscription\":\"sub_1\"}}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.stripe.com/v1/invoice_payments?invoice=in_1&status=paid&limit=100&expand%5B%5D=data.payment.payment_intent.latest_charge"))
                .andRespond(withSuccess("""
                    {"object":"list","has_more":false,"data":[{"livemode":false,"invoice":"in_1","amount_paid":559900,
                     "payment":{"payment_intent":{"latest_charge":{"id":"ch_1","livemode":false,"amount_refunded":559900,"disputed":false}}}}]}
                    """,MediaType.APPLICATION_JSON));
        assertThat(gateway(false).getInvoice("in_1").paymentStatus()).isEqualTo("refunded"); server.verify();
    }
    @Test void schedulesCancellationAndExpiresOpenCheckoutWithoutNewSales() {
        server.expect(requestTo("https://api.stripe.com/v1/subscriptions/sub_1")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string("cancel_at_period_end=true")).andRespond(withSuccess("{\"id\":\"sub_1\",\"livemode\":false}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions/cs_test_1"))
                .andRespond(withSuccess("{\"id\":\"cs_test_1\",\"livemode\":false,\"status\":\"open\"}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions/cs_test_1/expire")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"cs_test_1\",\"livemode\":false,\"status\":\"expired\"}",MediaType.APPLICATION_JSON));
        var gateway = gateway(false); gateway.cancel("sub_1"); gateway.cancelCheckout("cs_test_1"); server.verify();
    }
    String sign(String body,long now) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec("whsec_fixture_only".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return "t="+now+",v1="+HexFormat.of().formatHex(mac.doFinal((now+"."+body).getBytes(StandardCharsets.UTF_8)));
    }
}
