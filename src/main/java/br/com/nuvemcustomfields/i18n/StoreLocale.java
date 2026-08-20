package br.com.nuvemcustomfields.i18n;

import java.util.List;
import java.util.Locale;

/**
 * Idioma do app derivado do pais da loja (campo `country` de GET /store, persistido em
 * store_country_code). A Nuvemshop opera em pt-BR no Brasil e espanhol no resto da Latam,
 * entao o mapeamento e binario de proposito: nao existe terceiro idioma para cair.
 */
public final class StoreLocale {

    public static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    public static final Locale ES = Locale.forLanguageTag("es");

    public static final List<Locale> SUPPORTED = List.of(PT_BR, ES);

    private StoreLocale() {
    }

    public static Locale forCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return PT_BR;
        }
        String normalized = countryCode.strip().toUpperCase(Locale.ROOT);
        return "BR".equals(normalized) || "PT".equals(normalized) ? PT_BR : ES;
    }

    /**
     * Usado nas paginas publicas, que nao tem loja em sessao. Exige sinal explicito para
     * espanhol: idioma desconhecido (en, it, ...) cai no bundle padrao, nao num terceiro
     * idioma que o navegador nem pediu.
     */
    public static Locale forLanguageHeader(Locale requested) {
        if (requested == null) {
            return PT_BR;
        }
        return "es".equalsIgnoreCase(requested.getLanguage()) ? ES : PT_BR;
    }

    /** Tag enviada ao script de vitrine/checkout para localizar as mensagens do comprador. */
    public static String tagFor(String countryCode) {
        return forCountry(countryCode).toLanguageTag();
    }
}
