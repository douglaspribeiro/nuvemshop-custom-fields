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
        gateway = new DodoTestGateway(new DodoTestProperties(true, "dodo_test_credential", secret, 20), catalog, builder.build(), new ObjectMapper(), new br.com.nuvemcustomfields.properties.HomologProperties("42"));
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
        assertThat(new DodoTestProperties(true,"opaque-test-api-key",secret,20).configured()).isTrue();
        assertThat(new br.com.nuvemcustomfields.properties.HomologProperties("0").allows(42L)).isFalse();
    }
    @Test void allStoresSettingStillExcludesBrazilAndMissingCatalog() {
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.DODO_TEST,PaymentEnvironment.SANDBOX,"CO",PlanType.PREMIUM)).thenReturn(Optional.of(price()));
        var all = new DodoTestGateway(new DodoTestProperties(true,"dodo_test_credential",secret,20),catalog,builder.build(),new ObjectMapper(), new br.com.nuvemcustomfields.properties.HomologProperties("*"));
        try {
            assertThat(all.supports(store(43,"CO"))).isTrue();
            assertThat(all.supports(store(43,"BR"))).isFalse();
            assertThat(all.supports(store(43,"CL"))).isFalse();
        } finally { all.close(); }
    }
    @Test void createsHostedTestCheckoutFromBoundCatalogWithFixedCurrencyAndStoreReference() {
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.DODO_TEST,PaymentEnvironment.SANDBOX,"CO",PlanType.PREMIUM)).thenReturn(Optional.of(price()));
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
                .andRespond(withSuccess("{\"subscription_id\":\"sub_test\",\"customer\":{\"customer_id\":\"cus_test\",\"phone_number\":\"+57 300 1234567\"},\"status\":\"active\",\"currency\":\"USD\",\"recurring_pre_tax_amount\":100,\"product_id\":\"pdt_test\",\"next_billing_date\":\"2026-11-15T12:00:00Z\",\"cancel_at_next_billing_date\":true,\"metadata\":{\"external_reference\":\"ncf_ref\"}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://test.dodopayments.com/payments/pay_test"))
                .andRespond(withSuccess("{\"payment_id\":\"pay_test\",\"subscription_id\":\"sub_test\",\"status\":\"succeeded\",\"currency\":\"USD\",\"total_amount\":110}",MediaType.APPLICATION_JSON));
        var subscription = gateway.getSubscription("sub_test");
        assertThat(subscription.cancellationEffectiveAt()).isEqualTo(Instant.parse("2026-11-15T12:00:00Z"));
        assertThat(subscription.payerPhone()).isEqualTo("+57 300 1234567");
        var invoice=gateway.getInvoice("pay_test");assertThat(invoice.approved()).isTrue();assertThat(invoice.amountPaid()).isEqualByComparingTo("1.10");server.verify();
    }
    private UpgradeCoupon firstCoupon() {
        var c=new UpgradeCoupon(); org.springframework.test.util.ReflectionTestUtils.setField(c,"id",7L);
        c.setProvider(PaymentProviderType.DODO);c.setPurpose(UpgradeCoupon.Purpose.FIRST_PAYMENT);
        c.setEnvironment(PaymentEnvironment.SANDBOX);c.setTargetPlan(PlanType.PREMIUM);
        c.setCode("FIRST30");c.setDiscountPercent(new BigDecimal("30"));return c;
    }
    private String nativeDiscount(long owner) {
        return "{\"discount_id\":\"dsc_first\",\"type\":\"percentage\",\"code\":\"FIRST30\",\"metadata\":{\"ncf_coupon_id\":\""+owner+"\"}}";
    }
    @Test void synchronizesOwnedNativeDiscountForExactlyOneCycle() {
        server.expect(requestTo("https://test.dodopayments.com/discounts/code/FIRST30"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://test.dodopayments.com/discounts")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.amount").value(3000)).andExpect(jsonPath("$.subscription_cycles").value(1))
                .andExpect(jsonPath("$.per_customer_usage_limit").doesNotExist()).andExpect(jsonPath("$.customer_eligibility").value("any"))
                .andExpect(jsonPath("$.restricted_to[0]").value("pdt_test"))
                .andExpect(jsonPath("$.metadata.ncf_coupon_id").value("7"))
                .andRespond(withSuccess(nativeDiscount(7),MediaType.APPLICATION_JSON));
        assertThat(gateway.synchronizeDiscount(firstCoupon(),List.of("pdt_test")).path("discount_id").asText()).isEqualTo("dsc_first");
        server.verify();
    }
    @Test void neverOverwritesAnUnrelatedNativeCoupon() {
        server.expect(requestTo("https://test.dodopayments.com/discounts/code/FIRST30"))
                .andRespond(withSuccess(nativeDiscount(99),MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->gateway.synchronizeDiscount(firstCoupon(),List.of("pdt_test")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não pertence");server.verify();
    }
    @Test void nativePreviewDeterminesDiscountedRegionalPriceBeforeCheckoutCreation() {
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.DODO_TEST,PaymentEnvironment.SANDBOX,"CO",PlanType.PREMIUM)).thenReturn(Optional.of(price()));
        String full="{\"product_cart\":[{\"product_id\":\"pdt_test\",\"currency\":\"USD\",\"discounted_price\":100,\"is_subscription\":true,\"quantity\":1}]}";
        server.expect(requestTo("https://test.dodopayments.com/checkouts/preview")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.discount_codes").doesNotExist()).andRespond(withSuccess(full,MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://test.dodopayments.com/checkouts/preview"))
                .andExpect(jsonPath("$.discount_codes[0]").value("FIRST30"))
                .andExpect(jsonPath("$.feature_flags.allow_discount_code").value(false))
                .andRespond(withSuccess(full.replace("100,","70,"),MediaType.APPLICATION_JSON));
        assertThat(gateway.previewFirstAmount(store(42,"CO"),PlanType.PREMIUM,"FIRST30")).isEqualByComparingTo("0.70");server.verify();
    }
    @Test void changedRegionalPriceIsRejectedBeforeTakingPayment() {
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.DODO_TEST,PaymentEnvironment.SANDBOX,"CO",PlanType.PREMIUM)).thenReturn(Optional.of(price()));
        server.expect(requestTo("https://test.dodopayments.com/checkouts/preview"))
                .andRespond(withSuccess("{\"product_cart\":[{\"product_id\":\"pdt_test\",\"currency\":\"USD\",\"discounted_price\":200,\"is_subscription\":true,\"quantity\":1}]}",MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->gateway.previewFirstAmount(store(42,"CO"),PlanType.PREMIUM,"FIRST30"))
                .isInstanceOf(PaymentGatewayException.class).hasMessageContaining("preço");server.verify();
    }
    @Test void checkoutAppliesOnlyTheServerValidatedCoupon() {
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.DODO_TEST,PaymentEnvironment.SANDBOX,"CO",PlanType.PREMIUM)).thenReturn(Optional.of(price()));
        server.expect(requestTo("https://test.dodopayments.com/checkouts")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.discount_codes.length()").value(1))
                .andExpect(jsonPath("$.discount_codes[0]").value("FIRST30"))
                .andExpect(jsonPath("$.feature_flags.allow_discount_code").value(false))
                .andRespond(withSuccess("{\"session_id\":\"cks_first\",\"checkout_url\":\"https://test.checkout.dodopayments.com/cks_first\",\"status\":\"pending\"}",MediaType.APPLICATION_JSON));
        assertThat(gateway.createCheckout(store(42,"CO"),PlanType.PREMIUM,"ncf_ref","https://app.test/return","FIRST30").checkoutResourceId()).isEqualTo("cks_first");server.verify();
    }

    @Test void automaticWinbackCreatesOneNativeFiftyPercentDiscountBoundToTheCampaign() {
        var coupon=new WinbackCoupon(42L,"campaign_winback");coupon.useDodo(PaymentProviderType.DODO_TEST,PaymentEnvironment.SANDBOX);
        server.expect(requestTo("https://test.dodopayments.com/discounts/code/"+coupon.getCode()))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://test.dodopayments.com/discounts")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.code").value(coupon.getCode())).andExpect(jsonPath("$.amount").value(5000))
                .andExpect(jsonPath("$.subscription_cycles").value(1)).andExpect(jsonPath("$.usage_limit").value(1))
                .andExpect(jsonPath("$.metadata.ncf_winback_coupon").value(coupon.getCode()))
                .andExpect(jsonPath("$.metadata.ncf_winback_campaign").value(coupon.getCampaignId()))
                .andExpect(jsonPath("$.expires_at").value(coupon.getExpiresAt().toString()))
                .andRespond(withSuccess("{\"discount_id\":\"dsc_winback\"}",MediaType.APPLICATION_JSON));
        assertThat(gateway.synchronizeWinbackDiscount(coupon,List.of("pdt_test")).path("discount_id").asText()).isEqualTo("dsc_winback");server.verify();
    }
    @Test void retryAfterLostNativeCreationReusesOwnedCodeInsteadOfCreatingAnother() {
        var coupon=new WinbackCoupon(42L,"campaign_winback");coupon.useDodo(PaymentProviderType.DODO_TEST,PaymentEnvironment.SANDBOX);
        String remote="{\"discount_id\":\"dsc_winback\",\"type\":\"percentage\",\"metadata\":{\"ncf_winback_coupon\":\""+coupon.getCode()+"\",\"ncf_winback_campaign\":\""+coupon.getCampaignId()+"\"}}";
        server.expect(requestTo("https://test.dodopayments.com/discounts/code/"+coupon.getCode())).andRespond(withSuccess(remote,MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://test.dodopayments.com/discounts/dsc_winback")).andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.code").value(coupon.getCode())).andExpect(jsonPath("$.subscription_cycles").value(1))
                .andRespond(withSuccess(remote,MediaType.APPLICATION_JSON));
        assertThat(gateway.synchronizeWinbackDiscount(coupon,List.of("pdt_test")).path("discount_id").asText()).isEqualTo("dsc_winback");server.verify();
    }
    @Test void winbackCannotTakeOverAnotherCampaignsNativeCoupon() {
        var coupon=new WinbackCoupon(42L,"campaign_winback");coupon.useDodo(PaymentProviderType.DODO_TEST,PaymentEnvironment.SANDBOX);
        String remote="{\"discount_id\":\"dsc_winback\",\"type\":\"percentage\",\"metadata\":{\"ncf_winback_coupon\":\""+coupon.getCode()+"\",\"ncf_winback_campaign\":\"another_campaign\"}}";
        server.expect(requestTo("https://test.dodopayments.com/discounts/code/"+coupon.getCode())).andRespond(withSuccess(remote,MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->gateway.synchronizeWinbackDiscount(coupon,List.of("pdt_test")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não pertence");server.verify();
    }

}
