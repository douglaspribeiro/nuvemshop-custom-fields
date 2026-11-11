package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.SesProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Multipart;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

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
    void sendsReplyAndAuthenticatedTicketLink() throws Exception {
        var message = MailTestSupport.message();
        when(sender.createMimeMessage()).thenReturn(message);
        new SupportEmailNotifier(configured, "https://app.example.com/", sender).onReply(reply);
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getAllRecipients()).extracting(Object::toString).containsExactly("store@example.com");
        assertThat(captor.getValue().getFrom()).extracting(Object::toString).containsExactly("support@example.com");
        assertThat(captor.getValue().getSubject()).contains("#10", "Dúvida");
        assertThat(MailTestSupport.body(message, "text/plain")).contains("Resposta ágil", "https://app.example.com/support/tickets/10");
    }

    @Test
    void sendsImagesAsAttachmentsAlongsideTheReply() throws Exception {
        var message = MailTestSupport.message();
        when(sender.createMimeMessage()).thenReturn(message);
        byte[] cart = {1, 2, 3};
        byte[] checkout = {4, 5, 6};
        var withImages = new SupportReplyEvent(reply.ticketId(), reply.recipient(), reply.subject(), reply.message(), List.of(
                new SupportReplyEvent.Attachment("imagem-1.png", "image/png", cart),
                new SupportReplyEvent.Attachment("imagem-2.png", "image/png", checkout)));
        new SupportEmailNotifier(configured, "https://app.example.com", sender).onReply(withImages);
        verify(sender).send(message);
        message.saveChanges();
        Multipart mixed = (Multipart) message.getContent();
        assertThat(mixed.getCount()).isEqualTo(3);
        assertThat(MailTestSupport.body(message, "text/plain")).contains(reply.message());
        assertThat(mixed.getBodyPart(1).getFileName()).isEqualTo("imagem-1.png");
        assertThat(mixed.getBodyPart(1).getInputStream().readAllBytes()).containsExactly(cart);
        assertThat(mixed.getBodyPart(2).getFileName()).isEqualTo("imagem-2.png");
        assertThat(mixed.getBodyPart(2).getInputStream().readAllBytes()).containsExactly(checkout);
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
        var message = MailTestSupport.message();
        when(sender.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("secret store@example.com")).when(sender).send(any(MimeMessage.class));
        assertThatCode(() -> new SupportEmailNotifier(configured, "https://app.example.com", sender).onReply(reply))
                .doesNotThrowAnyException();
        verify(sender).send(message);
        assertThat(output).contains("support.email_failed").doesNotContain("secret", "store@example.com");
    }
}
