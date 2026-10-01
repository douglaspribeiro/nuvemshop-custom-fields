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
