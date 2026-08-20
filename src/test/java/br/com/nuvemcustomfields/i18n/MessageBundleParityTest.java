package br.com.nuvemcustomfields.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chave que existe so no bundle pt renderiza ??chave_es?? na tela do lojista argentino,
 * e nenhum teste de pagina pega isso se a chave estiver num ramo condicional do template.
 */
class MessageBundleParityTest {

    @Test
    void spanishBundleCoversEveryDefaultKey() throws IOException {
        Properties pt = load("/messages.properties");
        Properties es = load("/messages_es.properties");

        assertThat(new TreeSet<>(es.stringPropertyNames()))
                .as("chaves faltando em messages_es.properties")
                .containsAll(new TreeSet<>(pt.stringPropertyNames()));
        assertThat(new TreeSet<>(pt.stringPropertyNames()))
                .as("chaves orfas em messages_es.properties")
                .containsAll(new TreeSet<>(es.stringPropertyNames()));
    }

    @Test
    void spanishBundleIsActuallyTranslated() throws IOException {
        Properties pt = load("/messages.properties");
        Properties es = load("/messages_es.properties");

        long identical = pt.stringPropertyNames().stream()
                .filter(key -> pt.getProperty(key).equals(es.getProperty(key)))
                .count();

        // Nomes proprios e siglas coincidem de proposito (FREE, Pro, Store ID, ID...).
        assertThat(identical)
                .as("bundle es parece ser copia do pt")
                .isLessThan(pt.stringPropertyNames().size() / 4);
    }

    private Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).as("bundle %s nao encontrado", resource).isNotNull();
            properties.load(stream);
        }
        return properties;
    }
}
