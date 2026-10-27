package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.StoreAdminEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackofficeStoreEmailControllerTest {
    private final StoreAdminEmailService emails = mock(StoreAdminEmailService.class);
    private final BackofficeStoreEmailController controller = new BackofficeStoreEmailController(emails);

    @Test void rejectsInvalidTokenWithoutSending() {
        var session = new MockHttpSession();
        BackofficeStoreEmailController.token(session);
        assertThatThrownBy(() -> controller.send(12L, "invalid", "Assunto", "Mensagem", session,
                new RedirectAttributesModelMap())).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(emails);
    }

    @Test void sendsAndPreventsRepeatedSubmission() {
        var session = new MockHttpSession();
        var token = BackofficeStoreEmailController.token(session);
        var flash = new RedirectAttributesModelMap();
        assertThat(controller.send(12L, token, "Assunto", "Mensagem", session, flash))
                .isEqualTo("redirect:/backoffice/stores/12#store-email");
        assertThat(flash.getFlashAttributes()).containsKey("message");
        assertThatThrownBy(() -> controller.send(12L, token, "Assunto", "Mensagem", session, flash))
                .isInstanceOf(ResponseStatusException.class);
        verify(emails, times(1)).send(12L, "Assunto", "Mensagem");
    }

    @Test void preservesDraftAndShowsErrorWhenSendingFails() {
        var session = new MockHttpSession();
        var flash = new RedirectAttributesModelMap();
        doThrow(new IllegalStateException("Falha no envio")).when(emails).send(12L, "Assunto", "Mensagem");
        controller.send(12L, BackofficeStoreEmailController.token(session), "Assunto", "Mensagem", session, flash);
        assertThat(flash.getFlashAttributes().get("error")).isEqualTo("Falha no envio");
        assertThat(flash.getFlashAttributes().get("emailSubject")).isEqualTo("Assunto");
        assertThat(flash.getFlashAttributes().get("emailBody")).isEqualTo("Mensagem");
    }
}
