package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.SesProperties;
import br.com.nuvemcustomfields.repository.StoreRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoreAdminEmailServiceTest {
    static ActivationEmailTemplates templates() {
        var messages=new org.springframework.context.support.ResourceBundleMessageSource();
        messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
        return new ActivationEmailTemplates(messages);
    }
    private final StoreRepository stores = mock(StoreRepository.class);
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final SesProperties config = new SesProperties("smtp.example.com", 587, "user", "secret", "support@example.com");
    private final StoreAdminEmailService service = new StoreAdminEmailService(stores, config, sender, templates());

    private void recipient(String email) {
        var store = new Store();
        store.setStoreEmail(email);
        when(stores.findByStoreId(12L)).thenReturn(Optional.of(store));
    }

    @Test void sendsOnlyToRegisteredStoreEmailWithPlainTextAndEscapedHtml() throws Exception {
        recipient("owner@example.com");
        var message = MailTestSupport.message();
        when(sender.createMimeMessage()).thenReturn(message);
        service.send(12L, " Aviso ", "Olá,\nConfira seu plano. <b>Texto</b>");
        var mail = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(mail.capture());
        assertThat(mail.getValue().getAllRecipients()).extracting(Object::toString).containsExactly("owner@example.com");
        assertThat(mail.getValue().getFrom()).extracting(Object::toString).containsExactly("support@example.com");
        assertThat(mail.getValue().getSubject()).isEqualTo("Aviso");
        assertThat(MailTestSupport.body(message, "text/plain")).contains("Olá,\nConfira seu plano. <b>Texto</b>");
        assertThat(MailTestSupport.body(message, "text/html")).contains("&lt;b&gt;Texto&lt;/b&gt;").doesNotContain("<b>Texto</b>");
    }

    @Test void sendsAnEscapedHtmlButtonAndPlainTextAlternative() throws Exception {
        recipient("owner@example.com");
        var message = new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        service.sendAction(12L, "Renovação", "Olá <script>não executar</script>", "Reativar meu plano Ultra", "https://app.example.com/admin/billing/reactivate?storeId=12");
        message.saveChanges();
        verify(sender).send(message);
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("owner@example.com");
        var bytes = new java.io.ByteArrayOutputStream(); message.writeTo(bytes);
        assertThat(message.getContentType()).contains("multipart");
        // Inspect the alternative body independently from transport encoding.
        var root = (jakarta.mail.Multipart) message.getContent();
        var related = (jakarta.mail.Multipart) root.getBodyPart(0).getContent();
        var alternatives = (jakarta.mail.Multipart) related.getBodyPart(0).getContent();
        assertThat(alternatives.getBodyPart(0).getContent().toString()).contains("https://app.example.com/admin/billing/reactivate?storeId=12");
        assertThat(alternatives.getBodyPart(1).getContent().toString()).contains("Reativar meu plano Ultra", "&lt;script&gt;").doesNotContain("<script>");
    }

    @Test void rejectsMissingOrMultipleRecipients() {
        for (String email : new String[]{null, "invalid", "a@example.com,b@example.com"}) {
            recipient(email);
            assertThatThrownBy(() -> service.send(12L, "Assunto", "Mensagem"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(sender);
    }

    @Test void rejectsHeaderInjectionAndEmptyOrOversizedContent() {
        recipient("owner@example.com");
        for (String subject : new String[]{"", "Aviso\r\nBcc: another@example.com", "x".repeat(201)})
            assertThatThrownBy(() -> service.send(12L, subject, "Mensagem"))
                    .isInstanceOf(IllegalArgumentException.class);
        for (String body : new String[]{" ", "x".repeat(10001)})
            assertThatThrownBy(() -> service.send(12L, "Assunto", body))
                    .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(sender);
    }

    @Test void failsClearlyWhenNotConfigured() {
        recipient("owner@example.com");
        var service = new StoreAdminEmailService(stores, new SesProperties(null, 0, null, null, null), sender, templates());
        assertThatThrownBy(() -> service.send(12L, "Assunto", "Mensagem"))
                .hasMessageContaining("não está configurado");
        verifyNoInteractions(sender);
    }

    @Test void doesNotExposeProviderException() {
        recipient("owner@example.com");
        var message = MailTestSupport.message();
        when(sender.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("secret owner@example.com")).when(sender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> service.send(12L, "Assunto", "Mensagem"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("secret").hasMessageNotContaining("owner@example.com");
        verify(sender).send(message);
    }
    @Test void changedRecipientOrInjectedHeadersNeverReachSmtp() {
        recipient("updated@example.com");
        assertThatThrownBy(()->service.sendActivation(12L,1L,"old@example.com","Ajuda","Mensagem",
                "https://app.test/unsubscribe?token=token","config")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.sendActivation(12L,1L,"updated@example.com","Ajuda","Mensagem",
                "https://app.test/unsubscribe?token=token","config\r\nBcc: victim@example.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.sendActivation(12L,1L,"updated@example.com","Ajuda","Mensagem",
                "https://app.test/unsubscribe?token=token\r\nBcc: victim@example.com","config")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(sender);
    }

    @ParameterizedTest @ValueSource(strings={"BR","MX","PE"})
    void activationMimeUsesLocalizedSubjectBodyAndFooter(String country) throws Exception {
        var store=new Store();store.setStoreEmail("owner@example.com");store.setStoreCountryCode(country);
        when(stores.findByStoreId(12L)).thenReturn(Optional.of(store));
        var message=new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        var copy=templates().copy("D11",country,"Minha loja");
        service.sendActivation(12L,1L,"owner@example.com",copy.subject(),copy.body(),"https://app.test/unsubscribe?token=test","config");
        assertThat(message.getSubject()).isEqualTo(copy.subject());String body=MailTestSupport.body(message, "text/plain");
        assertThat(MailTestSupport.body(message, "text/html")).contains(country.equals("BR") ? "Parar de receber" : "Dejar de recibir");
        if(country.equals("BR")) assertThat(body).contains("Sua loja ainda está sem produtos","Parar de receber estes e-mails:")
                .doesNotContain("Dejar de recibir", "último e-mail");
        else assertThat(body).contains("Tu tienda todavía no tiene productos","Dejar de recibir estos correos:")
                .doesNotContain("Parar de receber", "último correo");
        verify(sender).send(message);
    }

}
