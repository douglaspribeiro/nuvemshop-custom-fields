package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.RenewalFailureEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackofficeRenewalFailureEmailControllerTest {
    final RenewalFailureEmailService emails = mock(RenewalFailureEmailService.class);
    final BackofficeRenewalFailureEmailController controller = new BackofficeRenewalFailureEmailController(emails);

    @Test void bindsTokenToTheStoreAndConsumesItBeforeSending() {
        var session = new MockHttpSession();
        String token = BackofficeRenewalFailureEmailController.token(session, 12L, "fingerprint");
        assertThatThrownBy(() -> controller.send(13L, token, "fingerprint", session, new RedirectAttributesModelMap()))
            .isInstanceOf(ResponseStatusException.class);
        assertThat(controller.send(12L, token, "fingerprint", session, new RedirectAttributesModelMap()))
            .isEqualTo("redirect:/backoffice/stores/12#renewal-failure-email");
        assertThatThrownBy(() -> controller.send(12L, token, "fingerprint", session, new RedirectAttributesModelMap()))
            .isInstanceOf(ResponseStatusException.class);
        verify(emails, times(1)).send(12L, "fingerprint");
    }

    @Test void showsASafeFailureWithoutClaimingSuccess() {
        var session = new MockHttpSession(); var flash = new RedirectAttributesModelMap();
        String token = BackofficeRenewalFailureEmailController.token(session, 12L, "fingerprint");
        doThrow(new IllegalArgumentException("O pagamento mudou.")).when(emails).send(12L, "fingerprint");
        controller.send(12L, token, "fingerprint", session, flash);
        assertThat(flash.getFlashAttributes().get("error")).isEqualTo("O pagamento mudou.");
        assertThat(flash.getFlashAttributes()).doesNotContainKey("message");
    }
}
