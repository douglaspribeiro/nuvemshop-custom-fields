package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.properties.CreemProperties;
import br.com.nuvemcustomfields.properties.WinbackProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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

class CreemGatewayTest {
    final ObjectMapper mapper = new ObjectMapper();
    final PaymentCatalogPriceRepository catalog = mock(PaymentCatalogPriceRepository.class);
    final RestClient.Builder builder = RestClient.builder();
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    CreemGateway gateway(boolean enabled) { return gateway(enabled, true, "42"); }
    CreemGateway gateway(boolean enabled, boolean sandbox, String allowedStoreIds) {
        return new CreemGateway(new CreemProperties(enabled, sandbox, "api-key", "secret", 3, 20), catalog,
                builder.build(), mapper, new WinbackProperties(false, null, null, allowedStoreIds));
    }
    String product() { return "{\"id\":\"prod_1\",\"mode\":\"test\",\"currency\":\"USD\",\"price\":999,\"billing_type\":\"recurring\",\"billing_period\":\"every-month\",\"status\":\"active\",\"tax_mode\":\"inclusive\",\"tax_category\":\"saas\"}"; }
    PaymentCatalogPrice price() {
        var price = new PaymentCatalogPrice(); price.setProvider(PaymentProviderType.CREEM); price.setEnvironment(PaymentEnvironment.SANDBOX);
        price.setCountryCode("MX"); price.setPlan(PlanType.PREMIUM); price.setCurrency("USD"); price.setAmountValue(new BigDecimal("9.99"));
        price.setProviderPriceId("prod_1"); price.setTaxMode("inclusive"); price.setEnabled(true); price.setValidatedAt(Instant.now()); return price;
    }
    @Test void createsMonthlyProductWithIdempotencyEvenWhenNewSalesDisabled() {
        server.expect(requestTo("https://test-api.creem.io/v1/products")).andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "api-key")).andExpect(header("Idempotency-Key", "stable-key"))
                .andExpect(jsonPath("$.price").value(999)).andExpect(jsonPath("$.billing_type").value("recurring"))
                .andExpect(jsonPath("$.billing_period").value("every-month")).andRespond(withSuccess(product(), MediaType.APPLICATION_JSON));
        assertThat(gateway(false).createProduct("Plano", "Mensal", "USD", new BigDecimal("9.99"), "inclusive", "stable-key").path("id").asText()).isEqualTo("prod_1"); server.verify();
    }
    @Test void rejectsCurrencyPriceTrialsAndWrongEnvironmentInCatalog() throws Exception {
        var gateway = gateway(true); var price = price(); var remote = mapper.readTree(product());
        gateway.validateProduct(price, remote);
        price.setCurrency("BRL"); assertThatThrownBy(() -> gateway.validateProduct(price, remote)).isInstanceOf(IllegalArgumentException.class);
        price.setCurrency("USD"); price.setAmountValue(BigDecimal.TEN);
        assertThatThrownBy(() -> gateway.validateProduct(price, remote)).isInstanceOf(IllegalArgumentException.class);
        price.setAmountValue(new BigDecimal("9.99"));
        assertThatThrownBy(() -> gateway.validateProduct(price, mapper.readTree(product().replace("\"test\"", "\"prod\"")))).isInstanceOf(PaymentGatewayException.class);
        assertThatThrownBy(() -> gateway.validateProduct(price, mapper.readTree(product().replace("\"price\":999", "\"trial_period_days\":7,\"price\":999")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreemGateway.minor(new BigDecimal("9.999"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void createsHostedCheckoutFromValidatedCatalog() {
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.CREEM, PaymentEnvironment.SANDBOX,"MX",PlanType.PREMIUM)).thenReturn(Optional.of(price()));
        server.expect(requestTo("https://test-api.creem.io/v1/products/prod_1")).andRespond(withSuccess(product(),MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://test-api.creem.io/v1/checkouts")).andExpect(jsonPath("$.product_id").value("prod_1"))
                .andExpect(jsonPath("$.request_id").value("ncf_ref")).andExpect(jsonPath("$.metadata.store_id").value("42"))
                .andExpect(jsonPath("$.custom_price").doesNotExist())
                .andRespond(withSuccess("{\"mode\":\"test\",\"id\":\"ch_1\",\"status\":\"pending\",\"checkout_url\":\"https://checkout.creem.io/ch_1\"}", MediaType.APPLICATION_JSON));
        var store = new Store(); store.setStoreId(42L); store.setStoreCountryCode("MX");
        assertThat(gateway(true).createCheckout(store,PlanType.PREMIUM,"ncf_ref","https://app.test/return").checkoutUrl()).isEqualTo("https://checkout.creem.io/ch_1"); server.verify();
    }
    @Test void readsPaidTransactionAndScheduledCancellationPeriod() {
        server.expect(requestTo("https://test-api.creem.io/v1/subscriptions?subscription_id=sub_1"))
                .andRespond(withSuccess("{\"mode\":\"test\",\"id\":\"sub_1\",\"product\":" + product() + ",\"status\":\"scheduled_cancel\",\"current_period_end_date\":\"2026-11-06T00:00:00Z\",\"customer\":\"cust_1\"}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://test-api.creem.io/v1/transactions?transaction_id=tran_1"))
                .andRespond(withSuccess("{\"mode\":\"test\",\"id\":\"tran_1\",\"subscription\":\"sub_1\",\"currency\":\"USD\",\"amount\":999,\"amount_paid\":1099,\"status\":\"paid\"}",MediaType.APPLICATION_JSON));
        var gateway = gateway(false);
        assertThat(gateway.getSubscription("sub_1").cancellationEffectiveAt()).isEqualTo(Instant.parse("2026-11-06T00:00:00Z"));
        var invoice = gateway.getInvoice("tran_1"); assertThat(invoice.approved()).isTrue(); assertThat(invoice.amountPaid()).isEqualByComparingTo("10.99"); server.verify();
    }
    @Test void verifiesRawBodyAndContinuesOperatingAfterNewSalesDisabled() throws Exception {
        String body = "{\"id\":\"evt_1\",\"eventType\":\"subscription.paid\",\"created_at\":1790000000000,\"object\":{\"id\":\"sub_1\",\"mode\":\"test\"}}";
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        var gateway = gateway(false); assertThat(gateway.configured()).isFalse(); assertThat(gateway.operational()).isTrue();
        assertThat(gateway.verifyNotification(body,signature,null,null).eventKey()).isEqualTo("CREEM:SANDBOX:evt_1");
        assertThatThrownBy(() -> gateway.verifyNotification(body + " ",signature,null,null)).isInstanceOf(PaymentGatewayException.class);
        assertThatThrownBy(() -> gateway.verifyNotification(body,null,null,null)).isInstanceOf(PaymentGatewayException.class);
    }
    @Test void cancelsAtPeriodEndAndDoesNotPretendToCancelAnOpenCheckout() {
        server.expect(requestTo("https://test-api.creem.io/v1/subscriptions/sub_1/cancel")).andExpect(jsonPath("$.mode").value("scheduled"))
                .andExpect(jsonPath("$.onExecute").value("cancel")).andRespond(withSuccess("{\"mode\":\"test\",\"id\":\"sub_1\"}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://test-api.creem.io/v1/checkouts?checkout_id=ch_1")).andRespond(withSuccess("{\"mode\":\"test\",\"id\":\"ch_1\",\"status\":\"pending\"}",MediaType.APPLICATION_JSON));
        var gateway = gateway(false); gateway.cancel("sub_1");
        assertThatThrownBy(() -> gateway.cancelCheckout("ch_1")).isInstanceOf(PaymentGatewayException.class); server.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", " ", "142,420", "invalid,8126986"})
    void sandboxBlocksUnlistedStoresIncludingDirectCheckoutAndUpgradeCalls(String allowedStoreIds) {
        var gateway = gateway(true, true, allowedStoreIds);
        var store = new Store(); store.setStoreId(42L); store.setStoreCountryCode("MX");
        assertThat(gateway.supports(store)).isFalse();
        assertThat(gateway.planAvailable(store, PlanType.PREMIUM)).isFalse();
        assertThatThrownBy(() -> gateway.createCheckout(store, PlanType.PREMIUM, "ref", "https://app.test/return"))
                .hasMessageContaining("lojas autorizadas");
        assertThatThrownBy(() -> gateway.changeSubscriptionPlan("sub_1", store, PlanType.PREMIUM_PLUS))
                .hasMessageContaining("lojas autorizadas");
        verifyNoInteractions(catalog);
        server.verify();
    }

    @Test void sandboxUsesExplicitListEvenWhenWinbackIsDisabled() {
        var gateway = gateway(true, true, " 8126986, 42 ,8289259 ");
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(eq(PaymentProviderType.CREEM),
                eq(PaymentEnvironment.SANDBOX), eq("MX"), any())).thenReturn(Optional.of(price()));
        var store = new Store(); store.setStoreId(42L); store.setStoreCountryCode("MX");
        assertThat(gateway.supports(store)).isTrue();
        assertThat(gateway.planAvailable(store, PlanType.PREMIUM_PLUS)).isTrue();
        store.setStoreId(null);
        assertThat(gateway.supports(store)).isFalse();
    }

    @Test void productionIgnoresSandboxAllowListAndCanCreateCheckout() {
        var gateway = gateway(true, false, "0");
        var price = price(); price.setEnvironment(PaymentEnvironment.PRODUCTION);
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(eq(PaymentProviderType.CREEM),
                eq(PaymentEnvironment.PRODUCTION), eq("MX"), any())).thenReturn(Optional.of(price));
        var store = new Store(); store.setStoreId(99L); store.setStoreCountryCode("MX");
        assertThat(gateway.supports(store)).isTrue();
        assertThat(gateway.planAvailable(store, PlanType.PREMIUM)).isTrue();
        server.expect(requestTo("https://api.creem.io/v1/products/prod_1"))
                .andRespond(withSuccess(product().replace("\"test\"", "\"prod\""), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.creem.io/v1/checkouts"))
                .andRespond(withSuccess("{\"mode\":\"prod\",\"id\":\"ch_1\",\"status\":\"pending\",\"checkout_url\":\"https://checkout.creem.io/ch_1\"}", MediaType.APPLICATION_JSON));
        assertThat(gateway.createCheckout(store, PlanType.PREMIUM, "ref", "https://app.test/return").checkoutResourceId()).isEqualTo("ch_1");
        server.verify();
    }
}
