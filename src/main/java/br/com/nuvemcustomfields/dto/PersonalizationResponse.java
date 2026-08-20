package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.i18n.StoreLocale;

import java.util.List;

/**
 * `locale` viaja junto porque o script de vitrine roda em Web Worker sem acesso ao DOM:
 * nao consegue ler o idioma da pagina, entao o idioma da loja vem do servidor.
 */
public record PersonalizationResponse(
        boolean enabled,
        List<FieldResponse> fields,
        PersonalizationStyleResponse style,
        String locale
) {

    public static PersonalizationResponse disabled() {
        return new PersonalizationResponse(false, List.of(), PersonalizationStyleResponse.empty(), StoreLocale.PT_BR.toLanguageTag());
    }
}
