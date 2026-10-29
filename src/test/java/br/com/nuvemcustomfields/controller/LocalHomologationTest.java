package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.NuvemshopApiClient;
import br.com.nuvemcustomfields.service.LocalNuvemshopApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:local_homolog;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.driverClassName=org.h2.Driver",
        "local.homologation.access-key=local-key-12345678901234567890","payments.efi.enabled=false"})
@ActiveProfiles({"docker","local-homolog"})
@AutoConfigureMockMvc
class LocalHomologationTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @Autowired NuvemshopApiClient api;
    @Autowired PaymentRoutingRuleRepository routes;
    @Autowired org.springframework.core.env.Environment environment;
    @Test void localHttpSessionCookiesDoNotRequireHttps(){
        assertThat(environment.getProperty("server.servlet.session.cookie.secure",Boolean.class)).isFalse();
        assertThat(environment.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void localVariantsCanBeConfiguredAndPreviewedWithoutTheRemoteApi() throws Exception {
        mvc.perform(get("/local/vitrine")).andExpect(redirectedUrl("/local/homologacao"));
        var result=mvc.perform(post("/local/homologacao").param("accessKey","local-key-12345678901234567890")).andReturn();
        var session=(MockHttpSession)result.getRequest().getSession();
        var store=stores.findByStoreId(LocalHomologationGuard.STORE_ID).orElseThrow();
        var variants=api.listProductVariants(store,900001L);
        assertThat(variants).hasSize(4);
        assertThat(variants.stream().map(NuvemshopApiClient.ProductVariant::id).distinct()).hasSize(4);
        assertThat(variants.get(0).price()).isEqualTo("50.00");
        assertThat(variants.get(1).price()).isEqualTo("60.00");
        mvc.perform(get("/admin/products/900001/fields").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Com personalização")));
        mvc.perform(post("/admin/products/900001/variants").session(session).param("restricted","true").param("variantIds","9000012","9000014"))
                .andExpect(flash().attributeExists("message"));
        var html=mvc.perform(get("/local/vitrine").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Caneca de teste","data-price=\"60.00\"","9000012","nuvemshop-personalizer.js?store=990000000001");
        mvc.perform(post("/admin/products/900001/variants").session(session).param("restricted","true").param("variantIds","9000022"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(get("/local/vitrine").session(session).param("productId","123")).andExpect(status().isNotFound());
        var foreign=new MockHttpSession();foreign.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,42L);
        mvc.perform(get("/local/vitrine").session(foreign)).andExpect(status().isForbidden());
        assertThatThrownBy(()->api.listProductVariants(new Store(),900001L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->api.getProduct(store,123L)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void protectedEntryCreatesFixtureAndSessionWithoutOAuth() throws Exception{
        mvc.perform(get("/local/homologacao")).andExpect(status().isOk());
        mvc.perform(post("/local/homologacao").param("accessKey","wrong")).andExpect(status().isForbidden());
        var previous=new MockHttpSession();previous.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);
        var result=mvc.perform(post("/local/homologacao").session(previous).param("accessKey","local-key-12345678901234567890"))
                .andExpect(redirectedUrl("/admin")).andReturn();
        assertThat(previous.isInvalid()).isTrue();var session=(MockHttpSession)result.getRequest().getSession();
        assertThat(session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY)).isEqualTo(LocalHomologationGuard.STORE_ID);
        assertThat(session.getAttribute(BackofficeSessionInterceptor.SESSION_KEY)).isNull();
        var store=stores.findByStoreId(LocalHomologationGuard.STORE_ID).orElseThrow();
        assertThat(store.getScope()).contains("read_orders");
        assertThat(store.getPlan()).isEqualTo(PlanType.FREE);assertThat(api).isInstanceOf(LocalNuvemshopApiClient.class);
        assertThat(api.listProducts(store,1,50,null).items()).hasSize(3);
        String dashboard=mvc.perform(get("/admin").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(dashboard).contains("HOMOLOGAÇÃO LOCAL","Sugerir uma melhoria");
        assertThat(routes.findByCountryCodeIgnoreCase("BR").orElseThrow().getEnvironment()).isEqualTo(PaymentEnvironment.SANDBOX);
        assertThat(routes.findByCountryCodeIgnoreCase("BR").orElseThrow().isEnabled()).isFalse();
        mvc.perform(get("/admin/products").session(session)).andExpect(status().isOk());
        mvc.perform(post("/local/homologacao").param("accessKey","local-key-12345678901234567890")).andExpect(redirectedUrl("/admin"));
        assertThat(stores.findAll().stream().filter(s->s.getStoreId().equals(LocalHomologationGuard.STORE_ID)).count()).isEqualTo(1);
    }
}
