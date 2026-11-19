package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.i18n.StoreLocale;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * `locale` viaja junto porque o script de vitrine roda em Web Worker sem acesso ao DOM:
 * nao consegue ler o idioma da pagina, entao o idioma da loja vem do servidor.
 */
public record PersonalizationResponse(
        boolean enabled,
        List<FieldResponse> fields,
        PersonalizationStyleResponse style,
        String locale,
        boolean paused,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean complements
) {

    public PersonalizationResponse(boolean enabled, List<FieldResponse> fields, PersonalizationStyleResponse style, String locale) {
        this(enabled, fields, style, locale, false, false);
    }

    public PersonalizationResponse withComplements() {
        return new PersonalizationResponse(enabled, fields, style, locale, paused, true);
    }

    public static PersonalizationResponse paused(String locale, PersonalizationStyleResponse style) {
        return new PersonalizationResponse(false, List.of(), style, locale, true, false);
    }

    public static PersonalizationResponse disabled() {
        return new PersonalizationResponse(false, List.of(), PersonalizationStyleResponse.empty(), StoreLocale.PT_BR.toLanguageTag());
    }
}
