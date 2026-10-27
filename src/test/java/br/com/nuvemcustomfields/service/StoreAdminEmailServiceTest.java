package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.SesProperties;
import br.com.nuvemcustomfields.repository.StoreRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoreAdminEmailServiceTest {
    private final StoreRepository stores = mock(StoreRepository.class);
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final SesProperties config = new SesProperties("smtp.example.com", 587, "user", "secret", "support@example.com");
    private final StoreAdminEmailService service = new StoreAdminEmailService(stores, config, sender);

    private void recipient(String email) {
        var store = new Store();
        store.setStoreEmail(email);
        when(stores.findByStoreId(12L)).thenReturn(Optional.of(store));
    }

    @Test void sendsOnlyToRegisteredStoreEmailAsPlainText() {
        recipient("owner@example.com");
        service.send(12L, " Aviso ", "Olá,\nConfira seu plano. <b>Texto</b>");
        var mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("owner@example.com");
        assertThat(mail.getValue().getFrom()).isEqualTo("support@example.com");
        assertThat(mail.getValue().getSubject()).isEqualTo("Aviso");
        assertThat(mail.getValue().getText()).isEqualTo("Olá,\nConfira seu plano. <b>Texto</b>");
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
        var service = new StoreAdminEmailService(stores, new SesProperties(null, 0, null, null, null), sender);
        assertThatThrownBy(() -> service.send(12L, "Assunto", "Mensagem"))
                .hasMessageContaining("não está configurado");
        verifyNoInteractions(sender);
    }

    @Test void doesNotExposeProviderException() {
        recipient("owner@example.com");
        doThrow(new MailSendException("secret owner@example.com")).when(sender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> service.send(12L, "Assunto", "Mensagem"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("secret").hasMessageNotContaining("owner@example.com");
    }
}
