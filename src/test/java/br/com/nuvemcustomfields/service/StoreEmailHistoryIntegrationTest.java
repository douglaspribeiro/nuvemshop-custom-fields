package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.mail.internet.MimeMessage;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoreEmailHistoryIntegrationTest {
    static final long STORE=998883001L;
    @Autowired StoreEmailHistoryService history;
    @Autowired StoreRepository stores;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    MockHttpSession session;
    @BeforeEach void setup() {
        var store=new Store();store.setStoreId(STORE);store.setStoreName("Loja de teste");stores.saveAndFlush(store);
        session=new MockHttpSession();session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);
    }
    // Transport records commit independently, just as they must when a business transaction rolls back.
    @AfterEach void cleanup() { org.springframework.test.context.transaction.TestTransaction.end(); jdbc.update("delete from store_email_history where store_id=?",STORE); }
    MimeMessage mail() throws Exception {
        var mail=MailTestSupport.message();var helper=new MimeMessageHelper(mail,true,"UTF-8");
        helper.setFrom("support@example.com");helper.setTo("original@example.com");helper.setSubject("Aviso <script>unsafe</script>");
        helper.setText("Resposta original\nhttps://app.test/unsubscribe?token=private-secret", "<p>Resposta original</p>");
        StoreEmailHistoryService.identify(mail,STORE,"MANUAL",null);return mail;
    }
    TrackedMailSender transport(boolean fail) {
        return new TrackedMailSender(history) { @Override protected void deliver(MimeMessage message) {
            if(fail) throw new MailSendException("private provider data");
        }};
    }
    String page() throws Exception {
        return mvc.perform(get("/backoffice/stores/"+STORE+"/emails").session(session))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andReturn().getResponse().getContentAsString();
    }
    @Test void recordsAcceptedMimeWithOriginalRecipientAndEscapedContentWithoutUnsubscribeSecret() throws Exception {
        transport(false).send(mail());var entries=history.page(STORE,0);assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().state()).isEqualTo("ACCEPTED");
        assertThat(entries.getFirst().recipient()).isEqualTo("original@example.com");
        assertThat(entries.getFirst().body()).contains("Resposta original","token=[oculto]").doesNotContain("private-secret");
        var html=page();assertThat(html).contains("Histórico de e-mails","original@example.com","Envio registrado","Sem confirmação de entrega","&lt;script&gt;")
            .doesNotContain("<script>unsafe</script>","private-secret");
    }
    @Test void recordsUncertainSmtpWithoutClaimingDelivery() throws Exception {
        assertThatThrownBy(()->transport(true).send(mail())).isInstanceOf(MailSendException.class);
        var row=history.page(STORE,0).getFirst();assertThat(row.state()).isEqualTo("UNKNOWN");assertThat(row.acceptedAt()).isNull();
        assertThat(page()).contains("Resultado incerto","Sem confirmação de entrega");
    }
    @Test void requiresBackofficeAndHandlesEmptyHistoryAndMissingStore() throws Exception {
        mvc.perform(get("/backoffice/stores/"+STORE+"/emails")).andExpect(redirectedUrl("/backoffice/login"));
        assertThat(page()).contains("Nenhum e-mail registrado");
        mvc.perform(get("/backoffice/stores/0/emails").session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/backoffice/stores/"+STORE+"/emails").param("page","-1").session(session)).andExpect(status().isBadRequest());
        String detail=mvc.perform(get("/backoffice/stores/"+STORE).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("/backoffice/stores/"+STORE+"/emails");
    }
    @Test void isolatesStoresAndPaginatesWithStableOrdering() throws Exception {
        for(int i=0;i<28;i++) insert(String.format("%036d",i),STORE,null,"ACCEPTED");
        insert("other-store",998883002L,null,"ACCEPTED");
        assertThat(history.page(STORE,0)).hasSize(26);assertThat(history.page(STORE,1)).hasSize(3);
        assertThat(history.page(STORE,0).getFirst().id()).isEqualTo(String.format("%036d",27));
        assertThat(page()).contains("Próxima").doesNotContain("other-store");
    }
    @Test void displaysHistoricalUnknownContentAndActualProviderFeedback() throws Exception {
        insert("historical",STORE,"activation:991199", "ACCEPTED");
        jdbc.update("insert into nc39_activation_email_feedback(id,store_id,event_id,event_type,occurred_at) values(?,?,?,?,?)",
                "history-feedback",STORE,991199L,"Delivery",Timestamp.from(Instant.now()));
        assertThat(page()).contains("Assunto não registrado","Conteúdo não registrado","Entrega confirmada");
    }
    @Test void importsOnlyHistoricalConfirmedSendsWithoutInventingSnapshots() throws Exception {
        jdbc.update("""
            insert into nc39_activation_email_outbox(store_id,installed_at,step,state,attempts,next_attempt_at,template_version,sent_at)
            values(?,?,'D11','SENT',1,?,'v1',?)
            """,STORE,Timestamp.from(Instant.now().minusSeconds(999)),Timestamp.from(Instant.now()),Timestamp.from(Instant.now()));
        String sql=new org.springframework.core.io.ClassPathResource("db/migration/V69__store_email_history.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        sql=sql.substring(sql.indexOf("-- Retain"));for(String statement:sql.split(";")) if(!statement.isBlank()) jdbc.update(statement);
        var row=history.page(STORE,0).getFirst();assertThat(row.category()).isEqualTo("ACTIVATION_D11");
        assertThat(row.subject()).isNull();assertThat(row.recipient()).isNull();assertThat(row.body()).isNull();
    }
    void insert(String id,long store,String source,String state) {
        jdbc.update("insert into store_email_history(id,store_id,category,source_key,state,attempted_at) values(?,?,'MANUAL',?,?,?)",
                id,store,source,state,Timestamp.from(Instant.parse("2026-11-05T12:00:00Z")));
    }
}
