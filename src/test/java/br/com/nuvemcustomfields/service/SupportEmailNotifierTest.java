package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.SesProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class SupportEmailNotifierTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final SesProperties configured = new SesProperties("email-smtp.us-east-1.amazonaws.com", 587,
            "smtp-user", "secret", "support@example.com");
    private final SupportReplyEvent reply = new SupportReplyEvent(10L, "store@example.com", "Dúvida", "Resposta ágil");

    @Test
    void sendsReplyAndAuthenticatedTicketLink() {
        new SupportEmailNotifier(configured, "https://app.example.com/", sender).onReply(reply);
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("store@example.com");
        assertThat(captor.getValue().getFrom()).isEqualTo("support@example.com");
        assertThat(captor.getValue().getSubject()).contains("#10", "Dúvida");
        assertThat(captor.getValue().getText()).contains("Resposta ágil", "https://app.example.com/support/tickets/10");
    }

    @Test
    void incompleteConfigurationLogsAndSkipsSending(CapturedOutput output) {
        var incomplete = new SesProperties(configured.host(), 587, "smtp-user", "", configured.from());
        new SupportEmailNotifier(incomplete, "https://app.example.com", sender).onReply(reply);
        verifyNoInteractions(sender);
        assertThat(output).contains("support.email_provider_not_configured", "Provedor AWS SES não configurado");
    }

    @Test
    void missingRecipientLogsAndSkipsSending(CapturedOutput output) {
        new SupportEmailNotifier(configured, "https://app.example.com", sender)
                .onReply(new SupportReplyEvent(10L, null, "Dúvida", "Resposta"));
        verifyNoInteractions(sender);
        assertThat(output).contains("support.email_recipient_missing");
    }

    @Test
    void providerFailureIsContainedAndDoesNotLogSensitiveDetails(CapturedOutput output) {
        doThrow(new MailSendException("secret store@example.com")).when(sender).send(any(SimpleMailMessage.class));
        assertThatCode(() -> new SupportEmailNotifier(configured, "https://app.example.com", sender).onReply(reply))
                .doesNotThrowAnyException();
        assertThat(output).contains("support.email_failed").doesNotContain("secret", "store@example.com");
    }
}
