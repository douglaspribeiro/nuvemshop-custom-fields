package br.com.nuvemcustomfields.payment;

import br.com.efi.efisdk.EfiPay;
import br.com.nuvemcustomfields.properties.EfiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class EfiGatewayTest {
    @Test
    void consultsCurrentChargeStatusInsteadOfCreationHistoryStatus() throws Exception {
        EfiProperties properties = new EfiProperties(true, true, "client-id", "client-secret",
                "payee-code", "72050", "72051", new BigDecimal("19.99"), new BigDecimal("29.99"));
        EfiGateway gateway = new EfiGateway(properties, new ObjectMapper());

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
