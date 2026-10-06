package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.properties.SesEventsProperties;
import br.com.nuvemcustomfields.properties.SesProperties;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.StoreErasureRequestService;
import br.com.nuvemcustomfields.service.WinbackCampaignService;
import br.com.nuvemcustomfields.service.WinbackPreparationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackofficeWinbackFailureTest {
    private final WinbackCampaignService campaigns = mock(WinbackCampaignService.class);
    private final MockHttpSession session = new MockHttpSession();
    private final RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

    private void dispatch(boolean enabled) {
        var controller = new BackofficeStoreDepartureController(mock(StoreErasureRequestService.class), campaigns, enabled);
        assertThat(controller.winback(5538394L, BackofficeStoreDepartureController.token(session), false, session, flash))
                .isEqualTo("redirect:/backoffice/stores?status=uninstalled");
    }

    @Test void disabledMailExplainsWhichFlagToConfigureWithoutPreparing() {
        dispatch(false);
        assertThat(flash.getFlashAttributes().get("error").toString()).contains("WINBACK_MAIL_ENABLED");
        verifyNoInteractions(campaigns);
    }

    @Test void missingConfigurationSetIsShownBeforeAnyStoreOrEmailIsModified() throws Exception {
        var stores = mock(StoreRepository.class);
        var service = new WinbackCampaignService(stores, null, null, null, null,
                new SesProperties("smtp.test", 587, "username", "secret", "support@example.test"),
                new SesEventsProperties(false, "", "", ""), "https://example.test", null, null);
        var failure = catchThrowableOfType(() -> service.prepareManual(5538394L, false), WinbackPreparationException.class);
        assertThat(failure).hasMessageContaining("AWS_SES_CONFIGURATION_SET");
        verifyNoInteractions(stores);
        when(campaigns.prepareManual(5538394L, false)).thenThrow(failure);
        dispatch(true);
        assertThat(flash.getFlashAttributes().get("error")).isEqualTo(failure.getMessage());
        verify(campaigns, never()).send(anyString());
    }

    @Test void incompleteSmtpExplainsThePrerequisiteWithoutExposingCredentials() {
        var service = new WinbackCampaignService(null, null, null, null, null,
                new SesProperties("", 587, "username", "private-password", "support@example.test"),
                new SesEventsProperties(false, "", "", "tracking"), "https://example.test", null, null);
        assertThatThrownBy(() -> service.prepareManual(5538394L, false))
                .isInstanceOf(WinbackPreparationException.class).hasMessageContaining("AWS_SES_SMTP_HOST")
                .hasMessageNotContaining("private-password");
    }

    @Test void pendingErasureExplainsTheRequiredAuthorization() {
        when(campaigns.prepareManual(5538394L, false)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "Confirme a autorização do contato manual para esta exclusão pendente."));
        dispatch(true);
        assertThat(flash.getFlashAttributes().get("error").toString()).contains("autorização", "exclusão pendente");
    }

    @Test void smtpFailureShowsAttemptIdAndDoesNotExposeOrRetryTheFailure() throws Exception {
        when(campaigns.prepareManual(5538394L, false)).thenReturn("mail-attempt-id");
        doThrow(new MailSendException("private-password private-recipient")).when(campaigns).send("mail-attempt-id");
        dispatch(true);
        assertThat(flash.getFlashAttributes().get("error").toString()).contains("mail-attempt-id", "Reconquistas")
                .doesNotContain("private-password", "private-recipient");
        verify(campaigns, times(1)).send("mail-attempt-id");
    }

    @Test void duplicateAttemptIsNotDispatchedAgain() throws Exception {
        when(campaigns.prepareManual(5538394L, false)).thenReturn(null);
        dispatch(true);
        assertThat(flash.getFlashAttributes().get("message").toString()).contains("já foi solicitada");
        verify(campaigns, never()).send(anyString());
    }
}
