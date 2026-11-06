package br.com.nuvemcustomfields.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import static org.assertj.core.api.Assertions.*;

class ActivationEmailTemplatesTest {
    final ActivationEmailTemplates templates;
    ActivationEmailTemplatesTest() {
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
        templates=new ActivationEmailTemplates(messages);
    }
    @ParameterizedTest @ValueSource(strings={"BR","PT",""})
    void everyStageAndFooterUsePortugueseForPortugueseStores(String country) {
        for(String step:new String[]{"D3","D7","D11"}) {
            var copy=templates.copy(step,country,"Ateliê");
            assertThat(copy.subject()).doesNotContain("Recordatorio", "¿");
            assertThat(copy.body()).contains("Olá Ateliê!","suporte").doesNotContain("último e-mail","{0}");
        }
        assertThat(templates.unsubscribe(country,"https://app.test/token")).isEqualTo("Parar de receber estes e-mails: https://app.test/token");
    }
    @ParameterizedTest @ValueSource(strings={"AR","MX","CL","CO","UY","PE","EC","ES"," mx "})
    void everyStageAndFooterUseSpanishForSpanishStores(String country) {
        for(String step:new String[]{"D3","D7","D11"}) {
            var copy=templates.copy(step,country,"Diseño");
            assertThat(copy.body()).contains("¡Hola Diseño!","soporte").doesNotContain("último correo","Nuvemshop","{0}");
        }
        assertThat(templates.copy("D11",country,null).subject()).startsWith("Recordatorio:");
        assertThat(templates.unsubscribe(country,"https://app.test/token")).isEqualTo("Dejar de recibir estos correos: https://app.test/token");
    }
    @Test void nullCountryFallsBackToPortugueseWithoutLeakingPlaceholdersOrClaimsOfLastEmail() {
        var copy=templates.copy("D11",null,null);
        assertThat(copy.subject()).startsWith("Lembrete:");
        assertThat(copy.body()).startsWith("Olá!").contains("Sua loja ainda está sem produtos", "\n")
                .doesNotContain("último","null","{0}","30%","trial");
        assertThatThrownBy(()->templates.copy("D15","BR","Loja")).isInstanceOf(IllegalArgumentException.class);
    }
}
