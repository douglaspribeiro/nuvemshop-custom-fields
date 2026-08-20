package br.com.nuvemcustomfields.i18n;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.dto.ProductPage;
import br.com.nuvemcustomfields.dto.ProductSummary;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.NuvemshopApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renderiza cada tela do lojista nos dois idiomas. Chave ausente no bundle vira
 * ??chave_es?? no HTML: e um defeito visivel que nenhum teste de service pega.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LocalizedPagesRenderTest {

    private static final long STORE_ID = 771100L;
    private static final long PRODUCT_ID = 5001L;

    /** Toda pagina que o lojista alcanca pelo painel. */
    private static final String[] MERCHANT_PAGES = {
            "/admin",
            "/admin/dashboard",
            "/admin/products",
            "/admin/products/5001/fields",
            "/admin/onboarding",
            "/admin/billing",
            "/admin/help",
            "/admin/settings/style"
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StoreRepository storeRepository;

    @MockitoBean
    private NuvemshopApiClient apiClient;

    @BeforeEach
    void setUp() {
        storeRepository.findByStoreId(STORE_ID).ifPresent(storeRepository::delete);
        when(apiClient.listProducts(any(Store.class), anyInt(), anyInt(), nullable(String.class)))
                .thenReturn(new ProductPage(
                        List.of(new ProductSummary(PRODUCT_ID, "Caneca")),
                        1, 50, 1L, false, null
                ));
        when(apiClient.listRecentOrders(any(Store.class)))
                .thenReturn(new ObjectMapper().createArrayNode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"AR", "MX", "CL", "CO"})
    void rendersEveryMerchantPageInSpanishForLatamStores(String country) throws Exception {
        MockHttpSession session = sessionForStoreIn(country);
        for (String page : MERCHANT_PAGES) {
            String body = render(page, session);
            assertNoMissingKeys(page, country, body);
            assertThat(body).as("%s (%s) deveria estar em espanhol", page, country)
                    .contains("lang=\"es\"");
            assertNoPortugueseLeaked(page, country, body);
        }
    }

    /**
     * Chave ausente em messages_es.properties NAO renderiza ??chave??: o Spring cai no
     * bundle padrao e entrega portugues em silencio. Este e o unico teste que pega isso
     * na tela renderizada.
     */
    private void assertNoPortugueseLeaked(String page, String country, String body) {
        List<String> leaked = new ArrayList<>();
        for (String portuguese : translatedPortugueseValues()) {
            if (body.contains(portuguese)) {
                leaked.add(portuguese);
            }
        }
        assertThat(leaked)
                .as("%s (%s) entregou texto em portugues", page, country)
                .isEmpty();
    }

    /**
     * Valores pt que tem traducao propria em es. Ignora textos curtos (colidem com dados
     * da loja) e os que carregam markup ou placeholder de MessageFormat.
     */
    private static List<String> translatedPortugueseValues() {
        Properties pt = loadBundle("/messages.properties");
        Properties es = loadBundle("/messages_es.properties");
        List<String> values = new ArrayList<>();
        for (String key : pt.stringPropertyNames()) {
            String portuguese = pt.getProperty(key);
            if (portuguese.equals(es.getProperty(key))) {
                continue;
            }
            if (portuguese.length() < 12 || portuguese.contains("<") || portuguese.contains("{")) {
                continue;
            }
            values.add(portuguese);
        }
        return values;
    }

    private static Properties loadBundle(String resource) {
        Properties properties = new Properties();
        try (InputStream stream = LocalizedPagesRenderTest.class.getResourceAsStream(resource)) {
            properties.load(stream);
        } catch (IOException ex) {
            throw new IllegalStateException("bundle " + resource + " ilegivel", ex);
        }
        return properties;
    }

    @Test
    void rendersEveryMerchantPageInPortugueseForBrazilianStores() throws Exception {
        MockHttpSession session = sessionForStoreIn("BR");
        for (String page : MERCHANT_PAGES) {
            String body = render(page, session);
            assertNoMissingKeys(page, "BR", body);
            assertThat(body).as("%s deveria estar em portugues", page).contains("lang=\"pt\"");
        }
    }

    /** Loja antiga sem pais gravado nao pode quebrar nem virar espanhol. */
    @Test
    void fallsBackToPortugueseWhenStoreHasNoCountry() throws Exception {
        MockHttpSession session = sessionForStoreIn(null);
        String body = render("/admin", session);
        assertNoMissingKeys("/admin", "sem pais", body);
        assertThat(body).contains("lang=\"pt\"");
    }

    /**
     * Paginas publicas nao tem loja em sessao: o idioma vem do Accept-Language.
     * E onde o revisor cai ao abrir a politica de privacidade do app store.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/privacy/", "/support/", "/admin/embedded"})
    void rendersPublicPagesInTheBrowserLanguage(String page) throws Exception {
        String spanish = mockMvc.perform(get(page).header("Accept-Language", "es-AR,es;q=0.9"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNoMissingKeys(page, "es-AR", spanish);
        assertThat(spanish).as("%s com Accept-Language es", page).contains("lang=\"es\"");

        String portuguese = mockMvc.perform(get(page).header("Accept-Language", "pt-BR,pt;q=0.9"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNoMissingKeys(page, "pt-BR", portuguese);
        assertThat(portuguese).as("%s com Accept-Language pt", page).contains("lang=\"pt\"");
    }

    private String render(String page, MockHttpSession session) throws Exception {
        return mockMvc.perform(get(page).session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void assertNoMissingKeys(String page, String context, String body) {
        assertThat(body)
                .as("%s (%s) tem chave de mensagem nao resolvida", page, context)
                .doesNotContain("??");
    }

    private MockHttpSession sessionForStoreIn(String country) {
        storeRepository.findByStoreId(STORE_ID).ifPresent(storeRepository::delete);
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setStoreName("Tienda Test");
        store.setAccessToken("token");
        store.setStoreCountryCode(country);
        store.setScope("read_products,read_orders,write_scripts");
        storeRepository.save(store);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, STORE_ID);
        return session;
    }
}
