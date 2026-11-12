package br.com.nuvemcustomfields.i18n;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.dto.ProductPage;
import br.com.nuvemcustomfields.dto.ProductSummary;
import br.com.nuvemcustomfields.entity.PersonalizationFieldHistory;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationFieldHistoryRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.NuvemshopApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
            "/admin/buyer-uploads",
            "/admin/onboarding",
            "/admin/help",
            "/admin/settings/style"
    };

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.version}")
    private String appVersion;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PersonalizationFieldHistoryRepository fieldHistory;

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
    @ValueSource(strings={"FREE","PREMIUM","PREMIUM_PLUS","PREMIUM_ULTRA"})
    void buyerFilesMenuAppearsOnlyForEligiblePlans(String plan) throws Exception {
        var session=sessionForStoreIn("BR");
        var store=storeRepository.findByStoreId(STORE_ID).orElseThrow();
        store.setPlan(br.com.nuvemcustomfields.entity.PlanType.valueOf(plan));storeRepository.saveAndFlush(store);
        String body=render("/admin/products",session);
        if(plan.equals("PREMIUM_PLUS") || plan.equals("PREMIUM_ULTRA")) assertThat(body).contains("href=\"/admin/buyer-uploads\"");
        else assertThat(body).doesNotContain("href=\"/admin/buyer-uploads\"");
    }

    @Test
    void productListRendersThumbnailAndPlaceholderForProductWithoutImage() throws Exception {
        when(apiClient.listProducts(any(Store.class), anyInt(), anyInt(), nullable(String.class)))
                .thenReturn(new ProductPage(List.of(
                        new ProductSummary(PRODUCT_ID, "Caneca", "https://cdn.example.com/caneca.jpg"),
                        new ProductSummary(PRODUCT_ID + 1, "Sem foto")
                ), 1, 50, 2L, false, null));

        String body = render("/admin/products", sessionForStoreIn("BR"));
        assertThat(body).contains("src=\"https://cdn.example.com/caneca.jpg\"", "loading=\"lazy\"");
        assertThat(body.split("class=\"merchant-product-thumbnail\"", -1)).hasSize(3);
        assertThat(body.split("<img ", -1)).hasSize(2);
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

    @ParameterizedTest
    @ValueSource(strings = {"BR", "AR"})
    void orderApiFailureDoesNotRenderNoOrdersOrReinstallInstruction(String country) throws Exception {
        var session = sessionForStoreIn(country);
        when(apiClient.listRecentOrders(any(Store.class))).thenThrow(
                new org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.FORBIDDEN));
        String body = render("/admin/dashboard", session);
        assertNoMissingKeys("/admin/dashboard", country, body);
        assertThat(body).contains(country.equals("BR") ? "Não foi possível consultar os pedidos" : "No pudimos consultar los pedidos");
        assertThat(body).doesNotContain("class=\"empty\"", "Nenhum pedido com preenchimento", "No encontramos pedidos con valores", "Reinstale o app", "Reinstalá la aplicación");
        if (System.getProperty("ncf.release.screens") != null) {
            var output=java.nio.file.Path.of(System.getProperty("ncf.release.screens"));java.nio.file.Files.createDirectories(output);
            java.nio.file.Files.writeString(output.resolve("pedidos-indisponiveis-"+country+".html"),body);
        }
    }

    @Test
    @Transactional
    void orderShowsEachPurchasedLineWithItsOwnPersonalization() throws Exception {
        var session = sessionForStoreIn("AR");
        fieldHistory.saveAndFlush(new PersonalizationFieldHistory(STORE_ID, PRODUCT_ID, "Molienda"));
        when(apiClient.listRecentOrders(any(Store.class))).thenReturn(new ObjectMapper().readTree("""
                [{"number":15094,"products":[
                  {"id":1,"product_id":5001,"variant_id":10,"name":"Café Colombia","quantity":2,
                    "image":{"src":"//cdn.example.com/cafe.jpg"},
                    "properties":[{"name":"Molienda","value":"Grano"}]},
                  {"id":2,"product_id":5002,"name":"Filtro","quantity":1,"properties":[]},
                  {"id":3,"product_id":5001,"variant_id":10,"name":"Café Colombia","quantity":1,
                    "properties":[{"name":"Molienda","value":"Prensa Francesa"}]}
                ]}]
                """));

        String body = render("/admin/dashboard", session);
        assertNoMissingKeys("/admin/dashboard", "AR", body);
        String[] items = body.split("class=\"merchant-order-item\"");
        assertThat(items).hasSize(3);
        assertThat(items[1]).contains("Artículo 1", "Café Colombia", "ID del producto: 5001", "Cant.: 2", "<h3>Molienda</h3>", "<p>Grano</p>")
                .contains("src=\"https://cdn.example.com/cafe.jpg\"", "loading=\"lazy\"")
                .doesNotContain("Prensa Francesa");
        assertThat(items[2]).contains("Artículo 3", "Café Colombia", "ID del producto: 5001", "Cant.: 1", "<h3>Molienda</h3>", "<p>Prensa Francesa</p>")
                .contains("merchant-product-thumbnail")
                .doesNotContain("<p>Grano</p>", "cdn.example.com/cafe.jpg");
    }

    @ParameterizedTest
    @ValueSource(strings={"BR","AR"})
    @Transactional
    void exportsOnlyPersonalizedItemsAsQuotedCsvInTheStoreLanguage(String country) throws Exception {
        var session=sessionForStoreIn(country);
        fieldHistory.saveAndFlush(new PersonalizationFieldHistory(STORE_ID,PRODUCT_ID,"Nome"));
        when(apiClient.listRecentOrders(any(Store.class))).thenReturn(new ObjectMapper().readTree("""
                [{"id":300,"number":108,"created_at":"2026-11-12T10:00:00Z","products":[
                    {"id":301,"product_id":5001,"name":"Camisa; personalizada","quantity":2,
                     "properties":[{"name":"Nome","value":"=1+1"}]},
                    {"id":302,"product_id":5002,"name":"Sem personalização","quantity":1,"properties":[]}
                ]}]
                """));
        var response=mockMvc.perform(get("/admin/dashboard/export.csv").session(session)).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.getHeader("Content-Disposition")).contains("attachment", "pedidos-personalizados.csv");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        String csv=response.getContentAsString();
        assertThat(csv).startsWith("\uFEFF\"Pedido\"").contains("\"108\"", "\"Camisa; personalizada\"", "\"'=1+1\"")
                .doesNotContain("Sem personalização");
        assertThat(csv.split("\r\n")).hasSize(2);
        assertThat(csv).contains(country.equals("BR")?"\"Resposta\"":"\"Respuesta\"");
    }

    @Test
    void doesNotExportAnEmptyCsvWhenTheOrderApiFails() throws Exception {
        var session=sessionForStoreIn("BR");
        when(apiClient.listRecentOrders(any(Store.class))).thenThrow(new org.springframework.web.client.RestClientException("offline"));
        mockMvc.perform(get("/admin/dashboard/export.csv").session(session)).andExpect(status().isServiceUnavailable());
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
            assertThat(appVersion).doesNotContain("@");
            assertThat(body).as("%s deve usar navegacao compacta no iframe", page)
                    .contains("class=\"merchant-topbar\"", "/styles/merchant.css?v=" + appVersion,
                            "/styles/app.css?v=" + appVersion)
                    .doesNotContain("merchant-sidebar");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"BR", "AR", "MX", "CL", "CO"})
    void rendersBillingAndRejectsCheckoutWithLocalizedMessageWhenGatewayIsDisabled(String country) throws Exception {
        MockHttpSession session = sessionForStoreIn(country);
        String expected = "BR".equals(country)
                ? "Em breve"
                : "Próximamente";
        String body = mockMvc.perform(get("/admin/billing").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("??");
        for (String page : List.of("/admin/products", "/admin/products/5001/fields", "/admin/onboarding")) {
            assertThat(render(page, session)).contains("href=\"/admin/billing\"");
        }
        mockMvc.perform(post("/admin/billing/subscribe").session(session).param("plan", "PREMIUM"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/admin/billing"))
                .andExpect(flash().attribute("error", expected));
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
        assertThat(spanish).contains("/styles/app.css?v=" + appVersion);

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
