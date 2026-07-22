package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.LgpdWebhookService;
import br.com.nuvemcustomfields.service.WebhookLifecycleService;
import br.com.nuvemcustomfields.service.WebhookSecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookControllerTest {

    private static final String BODY = "{\"store_id\":123,\"customer\":{\"id\":456}}";

    private final WebhookSecurityService securityService = mock(WebhookSecurityService.class);
    private final LgpdWebhookService lgpdWebhookService = mock(LgpdWebhookService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WebhookController(
            new ObjectMapper(),
            securityService,
            mock(WebhookLifecycleService.class),
            lgpdWebhookService
    )).build();

    @Test
    void erasesStoreAndForwardsCustomerLgpdRoutesWithoutRawPayloadPersistence() throws Exception {
        when(securityService.isValid(BODY, "valid")).thenReturn(true);

        mockMvc.perform(post("/hook/store/redact")
                        .header("x-linkedstore-hmac-sha256", "valid")
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isNoContent());

        verify(lgpdWebhookService).eraseStore(BODY);
        assertForwarded("/hook/customer/redact", "Exclusao dos dados do cliente");
        assertForwarded("/hook/customer/data", "Solicitacao dos dados do cliente");
    }

    @Test
    void rejectsLgpdRequestWithInvalidSignature() throws Exception {
        when(securityService.isValid(BODY, "invalid")).thenReturn(false);

        mockMvc.perform(post("/hook/store/redact")
                        .header("x-linkedstore-hmac-sha256", "invalid")
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(lgpdWebhookService);
    }

    private void assertForwarded(String path, String requestType) throws Exception {
        mockMvc.perform(post(path)
                        .header("x-linkedstore-hmac-sha256", "valid")
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isNoContent());

        verify(lgpdWebhookService).forwardToSupport(requestType, BODY);
    }
}
