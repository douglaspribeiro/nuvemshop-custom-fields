package br.com.nuvemcustomfields.complements;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.dto.ProductPage;
import br.com.nuvemcustomfields.dto.ProductSummary;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.NuvemshopApiClient;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ComplementAdminTest {
    private static final String HOME = "/admin/apps/complements";
    private static final AtomicLong STORE_IDS = new AtomicLong(985000L);

    @Autowired MockMvc mvc;
    @Autowired DataSource source;
    @Autowired StoreRepository stores;
    @Autowired ComplementOffers offers;
    @MockitoBean NuvemshopApiClient catalog;
    MockHttpSession session;
    long storeId;
    String csrf;

    @BeforeEach void setup() throws Exception {
        var migration = new ResourceDatabasePopulator(new ClassPathResource("db/migration/V91__product_complements.sql"));
        if (storeTableMissing()) migration.execute(source);
        session = merchant("read_products");
        storeId = (Long) session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
        mvc.perform(get(HOME).session(session)).andExpect(status().isOk());
        csrf = (String) session.getAttribute("complementAdminCsrf");
        when(catalog.listProducts(any(), anyInt(), anyInt(), nullable(String.class)))
                .thenReturn(new ProductPage(List.of(new ProductSummary(10L,"Agenda"), new ProductSummary(20L,"Passante")),
                        1,30,2,false,null));
        when(catalog.getCatalogProduct(any(), eq(10L))).thenReturn(product(10, 11, "Agenda"));
        when(catalog.getCatalogProduct(any(), eq(20L))).thenReturn(product(20, 21, "Passante"));
    }

    @Test void linksAndManagesAnOfferOnlyWithinItsStore() throws Exception {
        mvc.perform(get("/admin/apps").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Aplicativos")))
                .andExpect(content().string(containsString("Favoritos")));
        mvc.perform(get(HOME + "/new").session(session).param("parentId","10"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Passante")))
                .andExpect(content().string(not(containsString("Criar complemento"))));
        mvc.perform(get(HOME + "/new").session(session).param("parentId","10").param("productId","20"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("value=\"21\"")));

        mvc.perform(link("999", csrf)).andExpect(flash().attributeExists("error"));
        mvc.perform(link("21", "wrong")).andExpect(status().isForbidden());
        mvc.perform(link("21", csrf)).andExpect(redirectedUrl(HOME));
        mvc.perform(link("21", csrf)).andExpect(flash().attributeExists("error"));
        var offer = offers.list(storeId).getFirst();
        assertThat(offer.variantId()).isEqualTo(21);
        assertThat(offer.enabled()).isFalse();
        mvc.perform(get(HOME).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Agenda + Passante")));

        MockHttpSession other = merchant("read_products");
        mvc.perform(get(HOME).session(other)).andExpect(content().string(not(containsString("Passante"))));
        mvc.perform(post(HOME + "/" + offer.id() + "/remove").session(other)
                        .param("csrf", (String) other.getAttribute("complementAdminCsrf")))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post(HOME + "/" + offer.id() + "/update").session(session)
                        .param("csrf", csrf).param("label", "Adicionar passante dourado").param("enabled", "true"))
                .andExpect(redirectedUrl(HOME));
        assertThat(offers.list(storeId).getFirst().label()).isEqualTo("Adicionar passante dourado");
        assertThat(offers.list(storeId).getFirst().enabled()).isTrue();
        mvc.perform(post(HOME + "/" + offer.id() + "/remove").session(session).param("csrf", csrf))
                .andExpect(redirectedUrl(HOME));
        assertThat(offers.list(storeId)).isEmpty();
    }

    @Test void createsOnlyWithWriteScopeAndDoesNotRepeatAnUncertainRequest() throws Exception {
        UUID request = UUID.randomUUID();
        mvc.perform(create(request)).andExpect(flash().attributeExists("error"));
        verify(catalog, never()).createCatalogProduct(any(), anyMap());

        Store store = stores.findByStoreId(storeId).orElseThrow();
        store.setScope("read_products,write_products");
        stores.saveAndFlush(store);
        mvc.perform(get(HOME + "/new").session(session).param("parentId", "10").param("create", "true"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Criar produto e vincular")));

        when(catalog.createCatalogProduct(any(), anyMap())).thenThrow(new ResourceAccessException("timeout"));
        mvc.perform(create(request)).andExpect(redirectedUrl(HOME)).andExpect(flash().attributeExists("error"));
        mvc.perform(create(request)).andExpect(redirectedUrl(HOME)).andExpect(flash().attributeExists("error"));
        verify(catalog, times(1)).createCatalogProduct(any(), argThat(payload ->
                payload.get("visibility").equals("unlisted")
                && ((Map<?, ?>) ((List<?>) payload.get("variants")).getFirst()).get("price").equals("12.00")));
        assertThat(offers.list(storeId).getFirst().uncertain()).isTrue();
        ObjectNode created = product(20,21,"Passante");
        ((ObjectNode) created.path("variants").path(0)).put("sku","NC-" + request);
        when(catalog.getCatalogProductBySku(any(), eq("NC-" + request))).thenReturn(created);
        mvc.perform(post(HOME + "/" + request + "/recover").session(session).param("csrf", csrf))
                .andExpect(redirectedUrl(HOME)).andExpect(flash().attributeExists("message"));
        assertThat(offers.list(storeId).getFirst().ready()).isTrue();
    }

    private boolean storeTableMissing() throws Exception {
        try (var connection = source.getConnection();
             var tables = connection.getMetaData().getTables(null, null, "product_complement_offers", null)) {
            return !tables.next();
        }
    }

    private MockHttpSession merchant(String scopes) {
        var result = new MockHttpSession();
        var store = new Store();
        store.setStoreId(STORE_IDS.incrementAndGet());
        store.setAccessToken("test");
        store.setScope(scopes);
        store.setPlan(PlanType.FREE);
        store.setStoreCountryCode("BR");
        stores.saveAndFlush(store);
        result.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, store.getStoreId());
        return result;
    }

    private MockHttpServletRequestBuilder link(String variant, String token) {
        return post(HOME + "/link").session(session).param("csrf", token)
                .param("parentId","10").param("productId","20").param("variantId",variant).param("label","Adicionar passante");
    }

    private MockHttpServletRequestBuilder create(UUID request) {
        return post(HOME + "/create").session(session).param("csrf", csrf).param("requestId",request.toString())
                .param("parentId","10").param("label","Adicionar passante")
                .param("name","Passante").param("price","12.00").param("stock","10").param("unlisted","true");
    }

    private static ObjectNode product(long id, long variant, String name) {
        ObjectNode result = JsonNodeFactory.instance.objectNode().put("id",id).put("visibility","visible");
        result.putObject("name").put("pt",name);
        result.putArray("variants").addObject().put("id",variant).put("price","12.00");
        return result;
    }
}
