package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.properties.*;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DodoRegionalGatewayTest {
    final PaymentCatalogPriceRepository catalog = mock(PaymentCatalogPriceRepository.class);
    final RestClient.Builder builder = RestClient.builder();
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    DodoGateway gateway(boolean live) {
        return live ? new DodoLiveGateway(new DodoProperties(true,"dodo_live_test","secret",20),catalog,builder.build(),new ObjectMapper())
                : new DodoTestGateway(new DodoTestProperties(true,"dodo_test_test","secret","42",20),catalog,builder.build(),new ObjectMapper());
    }
    PaymentCatalogPrice price(DodoGateway gateway, String country, String currency, String amount) {
        var p = new PaymentCatalogPrice(); p.setProvider(gateway.provider()); p.setEnvironment(gateway.environment());
        p.setCountryCode(country); p.setPlan(PlanType.PREMIUM); p.setProviderPriceId("pdt_regional"); p.setCurrency(currency);
        p.setAmountValue(new BigDecimal(amount)); p.setEnabled(true); p.setValidatedAt(Instant.now()); return p;
    }
    String product() { return """
        {"product_id":"pdt_regional","is_recurring":true,"tax_category":"saas","pricing_mode":"by_country",
         "price":{"type":"recurring_price","price":499,"currency":"USD","payment_frequency_count":1,"payment_frequency_interval":"Month"}}
        """; }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void localFixedPriceAndZeroDecimalAmountsWorkTheSameInTestAndLive(boolean live) {
        try (var ignored = new AutoCloseGateway(gateway(live))) {
            var gateway = ignored.gateway; String base = live ? "https://live.dodopayments.com" : "https://test.dodopayments.com";
            var price = price(gateway,"CL","CLP","4199");
            when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(gateway.provider(),gateway.environment(),"CL",PlanType.PREMIUM)).thenReturn(Optional.of(price));
            String host = live ? "checkout.dodopayments.com" : "test.checkout.dodopayments.com";
            server.expect(requestTo(base+"/checkouts")).andExpect(jsonPath("$.billing_currency").value("CLP"))
                    .andExpect(jsonPath("$.billing_address.country").value("CL"))
                    .andRespond(withSuccess("{\"session_id\":\"cks_regional\",\"checkout_url\":\"https://"+host+"/checkout\"}",MediaType.APPLICATION_JSON));
            server.expect(requestTo(base+"/subscriptions/sub_regional")).andRespond(withSuccess("{\"subscription_id\":\"sub_regional\",\"status\":\"active\",\"currency\":\"CLP\",\"recurring_pre_tax_amount\":4199,\"product_id\":\"pdt_regional\",\"next_billing_date\":\"2026-11-20T00:00:00Z\"}",MediaType.APPLICATION_JSON));
            var store = new Store(); store.setStoreId(42L); store.setStoreCountryCode("CL");
            assertThat(gateway.createCheckout(store,PlanType.PREMIUM,"ncf_ref","https://app.test/return").checkoutResourceId()).isEqualTo("cks_regional");
            assertThat(gateway.getSubscription("sub_regional").amount()).isEqualByComparingTo("4199"); server.verify();
        }
    }
    @Test void refusesAutomaticFxFallbackWhenTheFixedRegionalRuleIsMissing() {
        var gateway = gateway(false);
        server.expect(requestTo("https://test.dodopayments.com/products/pdt_regional")).andRespond(withSuccess(product(),MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://test.dodopayments.com/products/pdt_regional/localized-prices")).andRespond(withSuccess("{\"items\":[]}",MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.validateCatalog(price(gateway,"MX","MXN","99"))).hasMessageContaining("preço regional fixo"); gateway.close(); server.verify();
    }
    @Test void enforcesCountryCurrencyAndEnvironment() {
        DodoMarketPricing.validate("CL","CLP",new BigDecimal("4199"));
        DodoMarketPricing.validate("MX","MXN",new BigDecimal("99.90"));
        DodoMarketPricing.validate("AR","USD",new BigDecimal("4.99"));
        DodoMarketPricing.validate("CO","USD",new BigDecimal("4.99"));
        assertThatThrownBy(() -> DodoMarketPricing.validate("AR","ARS",BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DodoMarketPricing.validate("CL","CLP",new BigDecimal("4199.99"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new DodoProperties(true,"opaque-live-api-key","secret",20).configured()).isTrue();
        assertThat(new DodoTestProperties(true,"opaque-test-api-key","secret","42",20).configured()).isTrue();
        assertThat(new DodoTestProperties(true," ","secret","42",20).configured()).isFalse();
        assertThat(DodoMarketPricing.fromMinor(new BigDecimal("9900"),"MXN")).isEqualByComparingTo("99");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void preservesTerminalAndDeliberatelyPausedStatusesInBothEnvironments(boolean live) {
        try (var ignored = new AutoCloseGateway(gateway(live))) {
            String base = live ? "https://live.dodopayments.com" : "https://test.dodopayments.com";
            for (String status : new String[]{"failed", "expired", "paused"}) {
                server.expect(requestTo(base + "/subscriptions/sub_" + status)).andRespond(withSuccess(
                        "{\"subscription_id\":\"sub_" + status + "\",\"status\":\"" + status
                        + "\",\"currency\":\"USD\",\"recurring_pre_tax_amount\":499,\"product_id\":\"pdt_regional\"}", MediaType.APPLICATION_JSON));
            }
            for (String status : new String[]{"failed", "expired", "paused"})
                assertThat(ignored.gateway.getSubscription("sub_" + status).status()).isEqualTo(status);
            server.verify();
        }
    }
    private record AutoCloseGateway(DodoGateway gateway) implements AutoCloseable { public void close() { gateway.close(); } }
}
