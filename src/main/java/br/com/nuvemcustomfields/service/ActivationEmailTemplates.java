package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.i18n.StoreLocale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import java.util.Locale;
import java.util.Set;

@Component
public class ActivationEmailTemplates {
    private final MessageSource messages;
    public ActivationEmailTemplates(MessageSource messages) { this.messages = messages; }
    public record Copy(String subject, String body) { }
    public Copy copy(String step, String country, String name) {
        if (!Set.of("D3", "D7", "D11").contains(step)) throw new IllegalArgumentException("Unsupported activation stage");
        Locale locale = StoreLocale.forCountry(country);
        String prefix = "activation.email." + step.toLowerCase(Locale.ROOT);
        String suffix = name == null || name.isBlank() ? "" : " " + name.strip();
        return new Copy(messages.getMessage(prefix + ".subject", null, locale),
                messages.getMessage(prefix + ".body", new Object[]{suffix}, locale));
    }
    public String unsubscribe(String country, String url) {
        return messages.getMessage("activation.email.unsubscribe", new Object[]{url}, StoreLocale.forCountry(country));
    }
}
