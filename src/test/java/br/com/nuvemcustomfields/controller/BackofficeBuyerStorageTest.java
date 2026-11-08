package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"buyer-uploads.cleanup-delay-ms=10000000","buyer-uploads.reconcile-delay-ms=10000000"})
@AutoConfigureMockMvc
class BackofficeBuyerStorageTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @Autowired BuyerFileStorage storage;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean S3BuyerFileStorage aws;
    @MockitoBean OciBuyerFileStorage oci;
    long store;
    MockHttpSession session;
    @BeforeEach void setup() {
        var s=new Store();s.setStoreId(System.nanoTime());s.setAccessToken("test");s=stores.saveAndFlush(s);store=s.getStoreId();
        session=new MockHttpSession();session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);
        when(aws.provider()).thenReturn(BuyerStorageProvider.AWS_S3);
        when(oci.provider()).thenReturn(BuyerStorageProvider.OCI_OBJECT_STORAGE);
        when(aws.enabled()).thenReturn(true);when(oci.enabled()).thenReturn(true);
    }
    @Test void rendersAndSavesStoreOverrideAndResetWithoutChangingGlobalDefault() throws Exception {
        mvc.perform(get("/backoffice/stores/"+store).session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Usar padrão do sistema")));
        mvc.perform(post("/backoffice/stores/"+store+"/buyer-upload-storage").session(session).param("provider","OCI_OBJECT_STORAGE"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));
        assertThat(stores.findByStoreId(store).orElseThrow().getBuyerUploadStorageProvider()).isEqualTo(BuyerStorageProvider.OCI_OBJECT_STORAGE);
        assertThat(storage.forStore(store)).isSameAs(oci);
        assertThat(storage.provider()).isEqualTo(BuyerStorageProvider.AWS_S3);
        mvc.perform(post("/backoffice/stores/"+store+"/buyer-upload-storage").session(session).param("provider",""))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));
        assertThat(stores.findByStoreId(store).orElseThrow().getBuyerUploadStorageProvider()).isNull();
        assertThat(storage.forStore(store)).isSameAs(aws);
        assertThat(jdbc.queryForObject("select count(*) from integration_logs where store_id=? and event_type='buyer.storage.override'",Long.class,store)).isEqualTo(2);
    }
    @Test void rejectsInvalidOrUnconfiguredProviderAndRequiresBackofficeAuthentication() throws Exception {
        mvc.perform(post("/backoffice/stores/"+store+"/buyer-upload-storage").param("provider","OCI_OBJECT_STORAGE"))
                .andExpect(redirectedUrl("/backoffice/login"));
        when(oci.enabled()).thenReturn(false);
        for(String provider:java.util.List.of("OCI_OBJECT_STORAGE","INVALID"))
            mvc.perform(post("/backoffice/stores/"+store+"/buyer-upload-storage").session(session).param("provider",provider))
                    .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        assertThat(stores.findByStoreId(store).orElseThrow().getBuyerUploadStorageProvider()).isNull();
    }
}
