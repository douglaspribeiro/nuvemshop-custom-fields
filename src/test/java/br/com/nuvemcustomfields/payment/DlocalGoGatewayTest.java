package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.properties.DlocalGoProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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

class DlocalGoGatewayTest {
    final PaymentCatalogPriceRepository catalog=mock(PaymentCatalogPriceRepository.class);
    final RestClient.Builder builder=RestClient.builder();
    final MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
    final DlocalGoProperties properties=new DlocalGoProperties(true,true,"fixture_key","fixture_secret","42",20);
    final DlocalGoGateway gateway=new DlocalGoGateway(properties,catalog,builder.build(),new ObjectMapper());
    final String reference="ncf_42_0123456789abcdef0123456789abcdef";
    String plan(String id,String name) { return """
        {"id":%s,"name":"%s","country":"AR","currency":"ARS","amount":5599,
         "active":true,"frequency_type":"MONTHLY","frequency_value":1,"free_trial_days":0,
         "subscribe_url":"https://checkout-sbx.dlocalgo.com/validate/subscription/token"}
        """.formatted(id,name); }
    PaymentCatalogPrice price() {
        var p=new PaymentCatalogPrice(); p.setProvider(PaymentProviderType.DLOCAL_GO); p.setEnvironment(PaymentEnvironment.SANDBOX);
        p.setCountryCode("AR"); p.setPlan(PlanType.PREMIUM); p.setCurrency("ARS"); p.setAmountValue(new BigDecimal("5599"));
        p.setProviderPriceId("10"); p.setEnabled(true); p.setValidatedAt(Instant.now()); return p;
    }
    void expect(String path,String response) {
        server.expect(requestTo(properties.baseUrl()+path)).andExpect(header("Authorization","Bearer fixture_key:fixture_secret"))
                .andRespond(withSuccess(response,MediaType.APPLICATION_JSON));
    }
    @Test void restrictsArgentinaAndSandboxStores() {
        var ar=new Store(); ar.setStoreCountryCode("AR"); ar.setStoreId(42L); assertThat(gateway.supports(ar)).isTrue();
        ar.setStoreId(99L); assertThat(gateway.supports(ar)).isFalse();
        ar.setStoreId(42L); ar.setStoreCountryCode("BR"); assertThat(gateway.supports(ar)).isFalse();
        assertThat(new DlocalGoProperties(false,true,"k","s","42",20).configured()).isFalse();
        assertThat(new DlocalGoProperties(false,true,"k","s","42",20).operational()).isTrue();
    }
    @Test void validatesMonthlyAmountAndCountryAgainstApi() {
        expect("/v1/subscription/plan/10",plan("10","template").replace("5599","1"));
        assertThatThrownBy(() -> gateway.validateCatalog(price())).hasMessageContaining("valor"); server.verify();
    }
    @Test void createsIsolatedPlanWithSignedNotificationEndpointAndCorrectCurrency() {
        Store store=new Store(); store.setStoreId(42L); store.setStoreCountryCode("AR");
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.DLOCAL_GO,
                PaymentEnvironment.SANDBOX,"AR",PlanType.PREMIUM)).thenReturn(Optional.of(price()));
        expect("/v1/subscription/plan/10",plan("10","template"));
        server.expect(requestTo(properties.baseUrl()+"/v1/subscription/plan")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.name").value(reference)).andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.notification_url").value("https://app.test/webhooks/dlocal-go?reference="+reference))
                .andRespond(withSuccess(plan("20",reference),MediaType.APPLICATION_JSON));
        var checkout=gateway.createCheckout(store,PlanType.PREMIUM,reference,"https://app.test/admin/billing/return");
        assertThat(checkout.checkoutResourceId()).isEqualTo("20"); assertThat(checkout.subscriptionId()).isNull(); server.verify();
    }
    @Test void rejectsSharedPlanWithMoreThanOneSubscriber() {
        expect("/v1/subscription/plan/20",plan("20",reference));
        expect("/v1/subscription/plan/20/subscription/all?page=1&page_size=100","{\"data\":[{\"id\":1},{\"id\":2}],\"total_pages\":1}");
        assertThatThrownBy(() -> gateway.checkoutSubscription("20")).hasMessageContaining("Mais de um"); server.verify();
    }
    @Test void readsAllExecutionPagesAndDoesNotTreatDeclinedRenewalAsPaid() {
        expect("/v1/subscription/plan/20",plan("20",reference));
        expect("/v1/subscription/plan/20/subscription/all?page=1&page_size=100","""
            {"data":[{"id":3,"plan":{"id":20},"country":"AR","status":"CONFIRMED","active":true}],"total_pages":1}
            """);
        expect("/v1/subscription/plan/20/subscription/3/execution/all?page=1&page_size=100","""
            {"data":[{"id":2,"status":"DECLINED","created_at":"2026-10-21T10:00:00Z"}],"total_pages":2}
            """);
        expect("/v1/subscription/plan/20/subscription/3/execution/all?page=2&page_size=100","""
            {"data":[{"id":1,"status":"COMPLETED","created_at":"2026-09-21T10:00:00Z"}],"total_pages":2}
            """);
        var remote=gateway.getSubscription("20:3");
        assertThat(remote.status()).isEqualTo("past_due");
        assertThat(remote.currentPeriodEnd()).isEqualTo(Instant.parse("2026-10-21T10:00:00Z")); server.verify();
    }
    @Test void cancelsSubscriptionAndClosesItsExclusivePlan() {
        server.expect(requestTo(properties.baseUrl()+"/v1/subscription/plan/20/subscription/3/deactivate"))
                .andExpect(method(HttpMethod.PATCH)).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(properties.baseUrl()+"/v1/subscription/plan/20/deactivate"))
                .andExpect(method(HttpMethod.PATCH)).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        gateway.cancel("20:3"); server.verify();
    }
    @Test void validatesHmacOverExactBodyAndDoesNotCollapsePaymentStatusNotifications() throws Exception {
        String body="{\"payment_id\":\"DP-283\"}";
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec("fixture_secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String header="V2-HMAC-SHA256, Signature: "+HexFormat.of().formatHex(mac.doFinal(("fixture_key"+body).getBytes(StandardCharsets.UTF_8)));
        var first=gateway.verifyNotification(body,header,null,reference);
        var second=gateway.verifyNotification(body,header,null,reference);
        assertThat(first.resourceId()).isEqualTo(reference); assertThat(first.eventKey()).isNotEqualTo(second.eventKey());
        assertThatThrownBy(() -> gateway.verifyNotification(body+" ",header,null,reference)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.verifyNotification(body,null,null,reference)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void redactsProviderErrors() {
        server.expect(requestTo(properties.baseUrl()+"/v1/subscription/plan/10")).andRespond(withServerError().body("secret payer@example.test"));
        assertThatThrownBy(() -> gateway.getPlan("10")).hasMessage("Não foi possível confirmar a resposta dLocal Go.")
                .hasNoCause(); server.verify();
    }
    @Test void updatesExclusivePlanUsingPatchWithoutChargingTheWholeNewMonth() {
        expect("/v1/subscription/plan/20",plan("20",reference));
        expect("/v1/subscription/plan/20/subscription/all?page=1&page_size=100","{\"data\":[{\"id\":3}],\"total_pages\":1}");
        server.expect(requestTo(properties.baseUrl()+"/v1/subscription/plan/20")).andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.amount").value(8399)).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        expect("/v1/subscription/plan/20",plan("20",reference).replace("5599","8399"));
        gateway.updateExclusivePlan("20",reference,new BigDecimal("5599"),new BigDecimal("8399"));server.verify();
    }
    @Test void zeroBasedFinancialPaginationReadsEveryPage() {
        expect("/v1/refunds?country=AR&page=0","{\"data\":[{\"id\":\"REF-1\"}],\"totalPages\":2}");
        expect("/v1/refunds?country=AR&page=1","{\"data\":[{\"id\":\"REF-2\"}],\"totalPages\":2}");
        assertThat(gateway.financialPages("/v1/refunds?country=AR")).hasSize(2);server.verify();
    }

}
