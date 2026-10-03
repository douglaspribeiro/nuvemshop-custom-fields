package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoreErasureRequestTest {
    @Autowired StoreRepository stores;
    @Autowired StoreErasureRequestService requests;
    @Autowired StoreDepartureService departures;
    @Autowired WinbackQueueService queue;
    @Autowired WinbackOutboxRepository outbox;
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    private static final long ID=990088771L;
    private Store fixture() {
        var s=new Store(); s.setStoreId(ID); s.setStoreName("Pendente de teste");
        s.setStoreEmail("teste@example.invalid"); s.setAccessToken("token-teste"); return stores.saveAndFlush(s);
    }
    private MockHttpSession backoffice() {
        var session=new MockHttpSession(); session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true); return session;
    }
    @Test void requestKeepsStoreAndFirstReceivedTimeButBlocksAccessAndWinback() throws Exception {
        fixture(); long before=departures.summary().getTotal(); var received=Instant.parse("2026-10-02T12:00:00Z");
        requests.receive(ID,received); requests.receive(ID,received.plusSeconds(60)); em.clear();
        var store=stores.findByStoreId(ID).orElseThrow();
        assertThat(store.getErasureRequestedAt()).isEqualTo(received);
        assertThat(store.getStoreName()).isEqualTo("Pendente de teste");
        assertThat(store.getStoreEmail()).isEqualTo("teste@example.invalid");
        assertThat(store.isActive()).isFalse(); assertThat(store.getAccessToken()).isNull();
        assertThat(stores.findActiveByStoreId(ID)).isEmpty();
        assertThat(departures.summary().getTotal()).isEqualTo(before+1);
        queue.enqueue(store); assertThat(outbox.existsByStoreIdAndUninstalledAt(ID,store.getUninstalledAt())).isFalse();
        var merchant=new MockHttpSession(); merchant.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,ID);
        mvc.perform(get("/admin").session(merchant)).andExpect(redirectedUrl("/admin/embedded"));
        mvc.perform(get("/backoffice/stores").param("status","uninstalled").param("q","Pendente de teste").session(backoffice()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Exclusão pendente")))
                .andExpect(content().string(containsString("Deletar loja")))
                .andExpect(content().string(containsString("Reconquista manual")))
                .andExpect(content().string(containsString("name=\"authorizeErasureContact\"")));
    }
    @Test void manualDeletionRequiresSessionTokenAndExplicitConfirmation() throws Exception {
        fixture(); requests.receive(ID,Instant.now()); var session=backoffice();
        mvc.perform(get("/backoffice/stores").param("status","uninstalled").session(session)).andExpect(status().isOk());
        String token=(String)session.getAttribute(BackofficeStoreDepartureController.TOKEN_KEY);
        mvc.perform(post("/backoffice/stores/{id}/erase",ID).session(session).param("actionToken","wrong").param("confirmation","APAGAR"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/backoffice/stores/{id}/erase",ID).session(session).param("actionToken",token))
                .andExpect(status().isBadRequest());
        assertThat(stores.findByStoreId(ID)).isPresent();
        mvc.perform(post("/backoffice/stores/{id}/erase",ID).session(session).param("actionToken",token).param("confirmation","APAGAR"))
                .andExpect(redirectedUrl("/backoffice/stores?status=uninstalled"));
        em.clear(); assertThat(stores.findByStoreId(ID)).isEmpty();
    }
    @Test void activeStoreCannotBeDeletedAndAnonymousRequestsDoNotCreateStores() throws Exception {
        fixture(); assertThatThrownBy(()->requests.eraseManually(ID)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        requests.receive(ID+1,Instant.now()); assertThat(stores.findByStoreId(ID+1)).isEmpty();
        mvc.perform(post("/backoffice/stores/{id}/erase",ID).param("actionToken","x").param("confirmation","APAGAR"))
                .andExpect(redirectedUrl("/backoffice/login"));
    }
    @Test void manualReasonIsSavedAndEscapedWithoutContactingTheStore() throws Exception {
        fixture(); requests.receive(ID,Instant.now()); var session=backoffice();
        mvc.perform(get("/backoffice/stores").param("status","uninstalled").session(session)).andExpect(status().isOk());
        String token=(String)session.getAttribute(BackofficeStoreDepartureController.TOKEN_KEY);
        mvc.perform(post("/backoffice/stores/{id}/departure-reason",ID).session(session)
                .param("actionToken",token).param("reason","Faltou uma funcionalidade").param("justification","<script>alert(1)</script>"))
                .andExpect(redirectedUrl("/backoffice/stores?status=uninstalled"));
        assertThat(stores.findByStoreId(ID).orElseThrow().getDepartureReason()).isEqualTo("Faltou uma funcionalidade");
        mvc.perform(get("/backoffice/stores").param("status","uninstalled").param("q","Pendente de teste").session(session))
                .andExpect(content().string(containsString("Registro manual do backoffice")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }
}
