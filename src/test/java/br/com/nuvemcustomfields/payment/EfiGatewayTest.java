package br.com.nuvemcustomfields.payment;

import br.com.efi.efisdk.EfiPay;
import br.com.nuvemcustomfields.properties.EfiProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EfiGatewayTest {
    @Test
    void adjustmentCreatesAndPaysOneOffChargeWithoutCreatingSubscription() {
        var properties = new EfiProperties(true,true,"client","secret","payee","1","2",new BigDecimal("19.99"),new BigDecimal("29.99"));
        var gateway = new EfiGateway(properties,new ObjectMapper(),mock(PaymentCatalogPriceRepository.class));
        try(MockedConstruction<EfiPay> ignored=mockConstruction(EfiPay.class,(api,context)->
            when(api.call(anyString(),anyMap(),anyMap())).thenAnswer(invocation->{
                String method=invocation.getArgument(0);var json=new ObjectMapper();var params=json.valueToTree(invocation.getArgument(1));var body=json.valueToTree(invocation.getArgument(2));
                if(method.equals("createCharge")){
                    assertThat(body.path("items").path(0).path("value").asLong()).isEqualTo(597);
                    assertThat(body.path("metadata").path("custom_id").asText()).isEqualTo("upgrade-test");
                    assertThat(body.has("plan_id")).isFalse();
                    return Map.of("data",Map.of("charge_id",77));
                }
                assertThat(method).isEqualTo("definePayMethod");assertThat(params.path("id").asText()).isEqualTo("77");
                assertThat(body.path("payment").path("credit_card").path("installments").asInt()).isEqualTo(1);
                return Map.of("data",Map.of("charge_id",77,"status","paid"));
            }))){
            String charge=gateway.createUpgradeCharge(new BigDecimal("5.97"),"upgrade-test","https://example.test/webhook","Ajuste proporcional");
            gateway.payUpgradeCharge(charge,new EfiGateway.EfiPayer("Teste","12345678901","t@example.com","11999999999","1990-01-01"),"token");
        }
    }
    @Test
    void listsRemotePlansWithoutRequiringLocalPlanIds() {
        var properties = new EfiProperties(true, false, "client", "secret", "payee", null, null,
                new BigDecimal("19.99"), new BigDecimal("29.99"));
        var gateway = new EfiGateway(properties, new ObjectMapper(), mock(PaymentCatalogPriceRepository.class));
        try (MockedConstruction<EfiPay> ignored = mockConstruction(EfiPay.class, (api, context) ->
                when(api.call(anyString(), anyMap(), anyMap())).thenAnswer(invocation -> {
                    assertThat((String) invocation.getArgument(0)).isEqualTo("listPlans");
                    assertThat((Map<String, String>) invocation.getArgument(1)).containsEntry("limit", "50").containsEntry("offset", "50");
                    assertThat((Map<?, ?>) invocation.getArgument(2)).isEmpty();
                    var plan = new java.util.HashMap<String, Object>();
                    plan.put("plan_id", 72053); plan.put("name", "Ultra"); plan.put("interval", 1);
                    plan.put("repeats", null); plan.put("created_at", "2026-10-02");
                    return Map.of("data", List.of(plan));
                }))) {
            assertThat(gateway.listPlans(50)).containsExactly(new EfiGateway.EfiPlan("72053", "Ultra", 1, null, "2026-10-02"));
        }
    }

    @Test
    void planListingRejectsUnexpectedResponses() {
        var properties = new EfiProperties(true, true, "client", "secret", "payee", null, null,
                new BigDecimal("19.99"), new BigDecimal("29.99"));
        var gateway = new EfiGateway(properties, new ObjectMapper(), mock(PaymentCatalogPriceRepository.class));
        try (MockedConstruction<EfiPay> ignored = mockConstruction(EfiPay.class, (api, context) ->
                when(api.call(anyString(), anyMap(), anyMap())).thenReturn(Map.of("data", Map.of())))) {
            assertThatThrownBy(() -> gateway.listPlans(0)).isInstanceOf(PaymentGatewayException.class);
        }
    }

    @Test
    void upgradeUsesBrazilianSubscriptionUpdateWithoutCancellationOrCheckout() {
        var properties = new EfiProperties(true, true, "client", "secret", "payee", "1", "2", "3",
                new BigDecimal("19.99"), new BigDecimal("29.99"), new BigDecimal("59.90"));
        ObjectMapper json = new ObjectMapper();
        var gateway = new EfiGateway(properties, json, mock(PaymentCatalogPriceRepository.class));
        var store = new br.com.nuvemcustomfields.entity.Store();
        store.setStoreCountryCode("BR"); store.setStoreCurrency("BRL");
        try (MockedConstruction<EfiPay> ignored = mockConstruction(EfiPay.class, (api, context) ->
                when(api.call(anyString(), anyMap(), anyMap())).thenAnswer(invocation -> {
                    assertThat((String) invocation.getArgument(0)).isEqualTo("updateSubscription");
                    var params = json.valueToTree(invocation.getArgument(1));
                    var body = json.valueToTree(invocation.getArgument(2));
                    assertThat(params.path("id").asText()).isEqualTo("123");
                    assertThat(body.path("plan_id").asLong()).isEqualTo(3L);
                    assertThat(body.path("items").path(0).path("value").asLong()).isEqualTo(5990L);
                    assertThat(body.has("mode")).isFalse();
                    return Map.of("data", Map.of("subscription_id", 123));
                }))) {
            gateway.changeSubscriptionPlan("123", store, br.com.nuvemcustomfields.entity.PlanType.PREMIUM_ULTRA);
        }
    }

    @Test
    void createsReducedFirstChargeOnExistingPlanAndUpdatesOnlyThatSubscription() throws Exception {
        EfiProperties properties = new EfiProperties(true, true, "client-id", "client-secret",
                "payee-code", "72050", "72051", new BigDecimal("19.99"), new BigDecimal("29.99"));
        ObjectMapper json = new ObjectMapper();
        EfiGateway gateway = new EfiGateway(properties, json, mock(PaymentCatalogPriceRepository.class));
        try (MockedConstruction<EfiPay> ignored = mockConstruction(EfiPay.class, (api, context) ->
                when(api.call(anyString(), anyMap(), anyMap())).thenAnswer(invocation -> {
                    String method = invocation.getArgument(0);
                    var params = json.valueToTree(invocation.getArgument(1));
                    var body = json.valueToTree(invocation.getArgument(2));
                    if ("createSubscription".equals(method)) {
                        assertThat(params.path("id").asText()).isEqualTo("72050");
                        assertThat(body.path("items").path(0).path("value").asLong()).isEqualTo(999);
                        return Map.of("data", Map.of("subscription_id", 123));
                    }
                    assertThat(method).isEqualTo("updateSubscription");
                    assertThat(params.path("id").asText()).isEqualTo("123");
                    assertThat(body.path("items").path(0).path("value").asLong()).isEqualTo(1999);
                    assertThat(body.has("plan_id")).isFalse();
                    return Map.of("data", Map.of("subscription_id", 123, "value", 1999));
                }))) {
            String id = gateway.createSubscription(br.com.nuvemcustomfields.entity.PlanType.PREMIUM,
                    "ref", "https://example.test/webhook", new BigDecimal("9.99"));
            gateway.updateRecurringAmount(id, br.com.nuvemcustomfields.entity.PlanType.PREMIUM, new BigDecimal("19.99"));
        }
    }

    @Test
    void requiresProviderConfirmationOfTheRestoredAmount() throws Exception {
        var properties = new EfiProperties(true, true, "client", "secret", "payee", "1", "2",
                new BigDecimal("19.99"), new BigDecimal("29.99"));
        var gateway = new EfiGateway(properties, new ObjectMapper(), mock(PaymentCatalogPriceRepository.class));
        try (MockedConstruction<EfiPay> ignored = mockConstruction(EfiPay.class, (api, context) ->
                when(api.call(anyString(), anyMap(), anyMap())).thenReturn(Map.of("data", Map.of("subscription_id", "123", "value", 999))))) {
            assertThatThrownBy(() -> gateway.updateRecurringAmount("123",
                    br.com.nuvemcustomfields.entity.PlanType.PREMIUM, new BigDecimal("19.99")))
                    .isInstanceOf(PaymentGatewayException.class);
        }
    }

    @Test
    void consultsCurrentChargeStatusInsteadOfCreationHistoryStatus() throws Exception {
        EfiProperties properties = new EfiProperties(true, true, "client-id", "client-secret",
                "payee-code", "72050", "72051", new BigDecimal("19.99"), new BigDecimal("29.99"));
        EfiGateway gateway = new EfiGateway(properties, new ObjectMapper(), mock(PaymentCatalogPriceRepository.class));

        try (MockedConstruction<EfiPay> ignored = mockConstruction(EfiPay.class, (api, context) ->
                when(api.call(anyString(), anyMap(), anyMap())).thenAnswer(invocation -> {
                    String method = invocation.getArgument(0);
                    return switch (method) {
                        case "detailSubscription" -> Map.of("data", Map.of("history",
                                List.of(Map.of("charge_id", 12345, "status", "new"))));
                        case "detailCharge" -> Map.of("data", Map.of("charge_id", 12345, "status", "paid"));
                        default -> throw new AssertionError("Método inesperado: " + method);
                    };
                }))) {
            GatewayInvoice invoice = gateway.getLatestInvoice("sub-1").orElseThrow();

            assertThat(invoice.paymentId()).isEqualTo("12345");
            assertThat(invoice.paymentStatus()).isEqualTo("paid");
            assertThat(invoice.approved()).isTrue();
        }
    }
}
