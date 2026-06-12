package br.com.nuvemcustomfields.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LgpdWebhookServiceTest {

    private final SupportService supportService = mock(SupportService.class);
    private final LgpdWebhookService service = new LgpdWebhookService(new ObjectMapper(), supportService);

    @Test
    void forwardsRequestAndPayloadToSupport() throws Exception {
        String body = """
                {"store_id":123,"customer":{"id":456,"email":"cliente@example.com"}}
                """;

        service.forwardToSupport("Exclusao dos dados do cliente", body);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(supportService).openAutomatedTicket(
                org.mockito.ArgumentMatchers.eq(123L),
                org.mockito.ArgumentMatchers.eq("Solicitacao LGPD"),
                message.capture()
        );
        assertThat(message.getValue())
                .contains("Pedido recebido: Exclusao dos dados do cliente")
                .contains("\"id\" : 456")
                .contains("cliente@example.com");
    }

    @Test
    void rejectsPayloadWithoutStoreId() {
        assertThatThrownBy(() -> service.forwardToSupport(
                "Solicitacao dos dados do cliente",
                "{\"customer\":{\"id\":456}}"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("store_id");
    }
}
