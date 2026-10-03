package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FeatureRequestServiceTest {
    @Autowired FeatureRequestService service;
    @Autowired FeatureRequestRepository requests;
    @Autowired StoreRepository stores;
    @Autowired MockMvc mvc;
    @MockitoBean DiscordSupportWebhookClient discord;
    Long storeId=991144L;MockHttpSession session;
    @BeforeEach void setup(){
        requests.findTop50ByStoreIdOrderByCreatedAtDesc(storeId).forEach(requests::delete);
        stores.findByStoreId(storeId).ifPresent(stores::delete);
        Store store=new Store();store.setStoreId(storeId);store.setAccessToken("test");store.setStoreCountryCode("BR");store.setStoreCurrency("BRL");store.setPlan(PlanType.PREMIUM_ULTRA);stores.save(store);
        session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,storeId);
    }
    @Test void storesPlanAndIdempotentSubmissionAndSendsAfterPersistence(){
        String token=UUID.randomUUID().toString();var request=service.submit(storeId," Copiar campos "," Quero copiar entre produtos. ",token);
        assertThat(request.getPlanAtSubmission()).isEqualTo(PlanType.PREMIUM_ULTRA);assertThat(request.getTitle()).isEqualTo("Copiar campos");
        assertThat(service.submit(storeId,"Copiar campos","Quero copiar entre produtos.",token).getId()).isEqualTo(request.getId());
        verifyNoInteractions(discord);when(discord.configured()).thenReturn(true);service.notifyPending();service.notifyPending();
        verify(discord,times(1)).sendFeatureRequest(any(),argThat(r->r.getId().equals(request.getId())));
        assertThat(requests.findById(request.getId()).orElseThrow().getDiscordSentAt()).isNotNull();
    }
    @Test void discordFailureDoesNotLoseSuggestionAndCanRetry(){
        var request=service.submit(storeId,"Melhoria","Minha sugestão",UUID.randomUUID().toString());
        when(discord.configured()).thenReturn(true);doThrow(new IllegalStateException("network")).when(discord).sendFeatureRequest(any(),any());service.notifyPending();
        var persisted=requests.findById(request.getId()).orElseThrow();assertThat(persisted.getDiscordSentAt()).isNull();assertThat(persisted.getNotificationAttempts()).isEqualTo(1);
        doNothing().when(discord).sendFeatureRequest(any(),any());persisted.setNextNotificationAt(java.time.Instant.now().minusSeconds(1));requests.save(persisted);service.notifyPending();
        assertThat(requests.findById(request.getId()).orElseThrow().getDiscordSentAt()).isNotNull();
    }
    @Test void rejectsBlankOversizedAndExcessiveSuggestions(){
        assertThatThrownBy(()->service.submit(storeId," ","x",UUID.randomUUID().toString())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.submit(storeId,"title","x".repeat(4001),UUID.randomUUID().toString())).isInstanceOf(IllegalArgumentException.class);
        for(int i=0;i<5;i++)service.submit(storeId,"title "+i,"desc",UUID.randomUUID().toString());
        assertThatThrownBy(()->service.submit(storeId,"sixth","desc",UUID.randomUUID().toString())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void controllerExplainsPurposePersistsAndEscapesUserText() throws Exception{
        String page=mvc.perform(get("/admin/suggestions").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Sugerir uma melhoria","sem compromisso de implementação","Discord de suporte");
        String token=(String)session.getAttribute("featureRequestSubmissionToken");
        mvc.perform(post("/admin/suggestions").session(session).param("title","Copiar campos").param("description","<script>alert(1)</script>").param("submissionToken",token))
                .andExpect(redirectedUrl("/admin/suggestions"));
        mvc.perform(post("/admin/suggestions").session(session).param("title","Copiar campos").param("description","<script>alert(1)</script>").param("submissionToken",token))
                .andExpect(redirectedUrl("/admin/suggestions"));
        assertThat(service.forStore(storeId)).hasSize(1);
        String history=mvc.perform(get("/admin/suggestions").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(history).contains("&lt;script&gt;").doesNotContain("<script>alert(1)</script>");
    }
    @Test void authenticationAndFormTokenAreRequiredAndLocalEntryAbsentNormally() throws Exception{
        mvc.perform(get("/admin/suggestions")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/suggestions").session(session).param("title","x").param("description","y").param("submissionToken",UUID.randomUUID().toString())).andExpect(status().isForbidden());
        mvc.perform(get("/backoffice/suggestions")).andExpect(redirectedUrl("/backoffice/login"));
        mvc.perform(get("/local/homologacao")).andExpect(status().isNotFound());
        mvc.perform(post("/local/homologacao").param("accessKey","local-key-12345678901234567890")).andExpect(status().isNotFound());
    }
    @Test void eachStoreSeesOnlyItsOwnSuggestions(){
        service.submit(storeId,"Minha ideia","x",UUID.randomUUID().toString());assertThat(service.forStore(998888L)).isEmpty();
    }
}
