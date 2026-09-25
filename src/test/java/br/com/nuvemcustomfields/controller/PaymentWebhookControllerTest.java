package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.payment.PaymentGatewayException;
import br.com.nuvemcustomfields.service.PaymentWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentWebhookControllerTest {
    @Test
    void rejectsInvalidMercadoPagoSignature() throws Exception {
        PaymentWebhookService service = mock(PaymentWebhookService.class);
        doThrow(new PaymentGatewayException("Assinatura Mercado Pago invalida."))
                .when(service).receiveMercadoPago("{}", "bad", "request", "sub-1");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PaymentWebhookController(service)).build();

        mvc.perform(post("/prod/webhooks/mercado-pago2")
                        .queryParam("data.id", "sub-1")
                        .header("x-signature", "bad")
                        .header("x-request-id", "request")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsPathAfterProxyStripsProdPrefix() throws Exception {
        PaymentWebhookService service = mock(PaymentWebhookService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PaymentWebhookController(service)).build();

        mvc.perform(post("/webhooks/mercado-pago2")
                        .queryParam("data.id", "sub-1")
                        .header("x-signature", "valid")
                        .header("x-request-id", "request")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNoContent());

        verify(service).receiveMercadoPago("{}", "valid", "request", "sub-1");
    }

    @Test
    void acceptsNewEfiWebhookWithOrWithoutProdPrefix() throws Exception {
        PaymentWebhookService service = mock(PaymentWebhookService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PaymentWebhookController(service)).build();

        mvc.perform(post("/prod/webhooks/efi3").param("notification", "efi-notification-token"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/webhooks/efi3").param("notification", "efi-notification-token"))
                .andExpect(status().isNoContent());

        verify(service, times(2)).receiveEfi("efi-notification-token");
    }
}
