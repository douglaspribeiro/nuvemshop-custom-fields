package br.com.nuvemcustomfields.i18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class StoreLocaleTest {

    @ParameterizedTest
    @CsvSource({
            "BR, pt-BR",
            "br, pt-BR",
            "PT, pt-BR",
            "AR, es",
            "MX, es",
            "CL, es",
            "CO, es"
    })
    void mapsStoreCountryToLanguage(String country, String expectedTag) {
        assertThat(StoreLocale.forCountry(country).toLanguageTag()).isEqualTo(expectedTag);
    }

    /** Loja antiga pode nao ter o pais gravado; cair em espanhol seria pior que o padrao. */
    @Test
    void fallsBackToPortugueseWhenCountryIsUnknown() {
        assertThat(StoreLocale.forCountry(null)).isEqualTo(StoreLocale.PT_BR);
        assertThat(StoreLocale.forCountry("  ")).isEqualTo(StoreLocale.PT_BR);
    }

    @Test
    void usesBrowserLanguageOnlyForPublicPages() {
        assertThat(StoreLocale.forLanguageHeader(Locale.forLanguageTag("pt-BR"))).isEqualTo(StoreLocale.PT_BR);
        assertThat(StoreLocale.forLanguageHeader(Locale.forLanguageTag("es-AR"))).isEqualTo(StoreLocale.ES);
        assertThat(StoreLocale.forLanguageHeader(Locale.forLanguageTag("es"))).isEqualTo(StoreLocale.ES);
    }

    /** Idioma sem traducao nossa cai no bundle padrao, nao em espanhol. */
    @Test
    void unknownBrowserLanguageFallsBackToDefaultBundle() {
        assertThat(StoreLocale.forLanguageHeader(Locale.ENGLISH)).isEqualTo(StoreLocale.PT_BR);
        assertThat(StoreLocale.forLanguageHeader(Locale.ITALIAN)).isEqualTo(StoreLocale.PT_BR);
        assertThat(StoreLocale.forLanguageHeader(null)).isEqualTo(StoreLocale.PT_BR);
    }
}
