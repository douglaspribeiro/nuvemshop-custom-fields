package br.com.nuvemcustomfields.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acesso as mensagens fora dos templates (flash messages, excecoes que chegam na tela).
 * Usa o locale do request corrente, resolvido pelo AppLocaleResolver.
 */
@Component
public class Messages {

    private final MessageSource messageSource;

    public Messages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
