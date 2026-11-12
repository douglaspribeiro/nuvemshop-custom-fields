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

@SpringBootTest(properties="suggestions.response-email-delay-ms=10000000")
@AutoConfigureMockMvc
class FeatureRequestServiceTest {
    @Autowired FeatureRequestService service;
    @Autowired FeatureRequestRepository requests;
    @Autowired StoreRepository stores;
    @Autowired MockMvc mvc;
    @Autowired FeatureRequestUpdateRepository updates;
    @Autowired FeatureRequestEmailNotifier emails;
    @MockitoBean DiscordSupportWebhookClient discord;
    @MockitoBean StoreAdminEmailService mail;
    Long storeId=991144L;MockHttpSession session;
    @BeforeEach void setup(){
        requests.findAll().stream().filter(r->storeId.equals(r.getStoreId())).forEach(requests::delete);
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
        var request=service.submit(storeId,"Minha ideia","x",UUID.randomUUID().toString());assertThat(service.forStore(998888L)).isEmpty();
        assertThat(service.forStore(998888L,request.getId())).isEmpty();
    }
    @Test void notificationLinksCanShowAnOlderSuggestionOutsideTheRecentFifty() {
        var old=service.submit(storeId,"Ideia antiga","Quero acompanhar",UUID.randomUUID().toString());
        for(int i=0;i<50;i++) {
            var newer=new FeatureRequest();newer.setStoreId(storeId);newer.setTitle("Nova ideia "+i);newer.setDescription("Descrição");
            newer.setPlanAtSubmission(PlanType.FREE);newer.setSubmissionToken(UUID.randomUUID().toString());requests.saveAndFlush(newer);
        }
        assertThat(service.forStore(storeId)).noneMatch(r->r.getId().equals(old.getId()));
        var linked=service.forStore(storeId,old.getId());
        assertThat(linked).hasSize(50);assertThat(linked.getFirst().getId()).isEqualTo(old.getId());
    }
    @Test void keepsAReplyHistoryAndRejectsStaleOrReusedForms() {
        var request=service.submit(storeId,"Arquivos","Quero arquivos",UUID.randomUUID().toString());
        String token=UUID.randomUUID().toString();
        var first=service.respond(request.getId(),FeatureRequestStatus.UNDER_REVIEW,"Estamos avaliando.",0,token);
        assertThat(first.getEmailState()).isEqualTo("PENDING");
        assertThat(service.respond(request.getId(),FeatureRequestStatus.UNDER_REVIEW,"Estamos avaliando.",0,token).getId()).isEqualTo(first.getId());
        assertThatThrownBy(()->service.respond(request.getId(),FeatureRequestStatus.AVAILABLE,"Outro retorno",0,token)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.respond(request.getId(),FeatureRequestStatus.AVAILABLE,"Disponível!",0,UUID.randomUUID().toString())).hasMessageContaining("Recarregue");
        assertThatThrownBy(()->service.respond(request.getId(),FeatureRequestStatus.PLANNED,"x".repeat(4001),1,UUID.randomUUID().toString())).isInstanceOf(IllegalArgumentException.class);
        service.respond(request.getId(),FeatureRequestStatus.PLANNED,"",1,UUID.randomUUID().toString());
        var current=service.forStore(storeId).getFirst();
        assertThat(current.getStatus()).isEqualTo(FeatureRequestStatus.PLANNED);
        assertThat(current.getUpdates()).hasSize(2);
        assertThat(current.getUpdates().getLast().getEmailState()).isEqualTo("NOT_REQUIRED");
        assertThat(service.forStore(998888L)).isEmpty();
    }
    @Test void staffPublishesResponsesThatAreVisibleAndEscapedForTheMerchant() throws Exception {
        var request=service.submit(storeId,"Minha ideia","Descrição",UUID.randomUUID().toString());
        String path="/backoffice/suggestions/"+request.getId()+"/respond";
        mvc.perform(post(path)).andExpect(redirectedUrl("/backoffice/login"));
        var staff=new MockHttpSession();staff.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);
        String page=mvc.perform(get("/backoffice/suggestions").session(staff)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Responder ou atualizar status", "Publicar atualização");
        String csrf=(String)staff.getAttribute("featureRequestResponseCsrf"),token=UUID.randomUUID().toString();
        mvc.perform(post(path).session(staff).param("status","AVAILABLE").param("revision","0").param("submissionToken",token).param("csrfToken","wrong")).andExpect(status().isForbidden());
        for(int i=0;i<2;i++)mvc.perform(post(path).session(staff).param("status","AVAILABLE").param("message","Disponível <script>alert(1)</script>").param("revision","0").param("submissionToken",token).param("csrfToken",csrf))
                .andExpect(redirectedUrl("/backoffice/suggestions#suggestion-"+request.getId()));
        assertThat(service.forStore(storeId).getFirst().getUpdates()).hasSize(1);
        String history=mvc.perform(get("/admin/suggestions").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(history).contains("Retorno da equipe","Disponível", "&lt;script&gt;").doesNotContain("<script>alert(1)</script>");
        String backoffice=mvc.perform(get("/backoffice/suggestions").session(staff)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(backoffice).contains("E-mail aguardando envio", "&lt;script&gt;");
        verify(mail,never()).sendAction(anyLong(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void emailsAResponseOnceInTheStoreLanguageAndDoesNotEmailStatusOnlyChanges() {
        var store=stores.findByStoreId(storeId).orElseThrow();store.setStoreCountryCode("AR");stores.saveAndFlush(store);
        var request=service.submit(storeId,"Archivos","Necesito archivos",UUID.randomUUID().toString());
        var reply=service.respond(request.getId(),FeatureRequestStatus.AVAILABLE,"Ya podés usarlo.",0,UUID.randomUUID().toString());
        service.respond(request.getId(),FeatureRequestStatus.PLANNED,"",1,UUID.randomUUID().toString());
        emails.notifyPending();verify(mail,never()).sendAction(anyLong(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString());
        when(mail.configured()).thenReturn(true);emails.notifyPending();emails.notifyPending();
        verify(mail,times(1)).sendAction(eq(storeId),contains("Respuesta a tu sugerencia"),contains("Estado: Disponible"),eq("Ver mi sugerencia"),endsWith("/admin/suggestions?requestId="+request.getId()+"#suggestion-"+request.getId()),eq("FEATURE_REQUEST"),eq("feature-request-update:"+reply.getId()));
        assertThat(updates.findById(reply.getId()).orElseThrow().getEmailState()).isEqualTo("SENT");
    }
    @Test void anEmailFailurePreservesTheResponseAndIsNotAutomaticallyRetried() {
        var request=service.submit(storeId,"Arquivos","Quero arquivos",UUID.randomUUID().toString());
        var reply=service.respond(request.getId(),FeatureRequestStatus.AVAILABLE,"Já está disponível!",0,UUID.randomUUID().toString());
        when(mail.configured()).thenReturn(true);
        doThrow(new IllegalStateException("SMTP uncertain")).when(mail).sendAction(anyLong(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString());
        emails.notifyPending();emails.notifyPending();
        assertThat(updates.findById(reply.getId()).orElseThrow().getEmailState()).isEqualTo("FAILED");
        verify(mail,times(1)).sendAction(anyLong(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString());
        assertThat(service.forStore(storeId).getFirst().getUpdates().getFirst().getMessage()).isEqualTo("Já está disponível!");
    }
}
