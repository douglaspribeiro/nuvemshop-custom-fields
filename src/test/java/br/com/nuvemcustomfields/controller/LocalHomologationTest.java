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
