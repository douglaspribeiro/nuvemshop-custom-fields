package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.UserJourneyAuditReportService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Slice;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BackofficeAuditPageTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @Autowired UserJourneyAuditRepository audits;
    final long storeId = 998881001L;
    MockHttpSession session;
    @BeforeEach void setup() {
        session = new MockHttpSession(); session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);
        var store = new Store(); store.setStoreId(storeId); store.setStoreName("Ateliê de presentes"); stores.saveAndFlush(store);
    }
    UserJourneyAudit event(String action, String outcome, String actor, int seconds) {
        var event=new UserJourneyAudit(); event.storeId=storeId; event.action=action; event.outcome=outcome; event.actor=actor;
        event.occurredAt=Instant.parse("2026-10-28T14:10:00Z").plusSeconds(seconds);
        event.completedAt=event.occurredAt.plusSeconds(1); event.method="POST"; event.route="/admin/products/{productId}/fields";
        event.journeyId="ef176f88-704e-47ad-af16-9bd7c372ba33"; event.productId=451L; event.httpStatus=302;
        return audits.saveAndFlush(event);
    }
    String page(String... filters) throws Exception {
        var request=get("/backoffice/audit").session(session);
        for(int i=0;i<filters.length;i+=2) request.param(filters[i],filters[i+1]);
        return mvc.perform(request).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andReturn().getResponse().getContentAsString();
    }
    @Test void protectsAuditFromAnonymousAndMerchantSessionsAndValidatesFilters() throws Exception {
        mvc.perform(get("/backoffice/audit")).andExpect(redirectedUrl("/backoffice/login"));
        var merchant=new MockHttpSession(); merchant.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,storeId);
        mvc.perform(get("/backoffice/audit").session(merchant)).andExpect(redirectedUrl("/backoffice/login"));
        for (String[] filter : new String[][]{{"page","-1"},{"page","10001"},{"storeId","0"},{"outcome","INVALID"},{"actor","INVALID"}})
            mvc.perform(get("/backoffice/audit").session(session).param(filter[0],filter[1])).andExpect(status().isBadRequest());
    }
    @Test void rendersTimelineWithReadableStatesAndNavigationWithoutWritingAudit() throws Exception {
        var created=event("AdminController.addField","COMPLETED","MERCHANT",0);
        created.beforeState="{\"exists\":true,\"enabled\":true,\"fields\":[]}";
        created.afterState="{\"exists\":true,\"enabled\":true,\"fields\":[{\"id\":71,\"type\":\"TEXT\",\"required\":true}]}";
        audits.saveAndFlush(created);
        event("AdminController.updateField","REJECTED","MERCHANT",90);
        var removed=event("AdminController.deleteRule","COMPLETED","OPERATOR",180);
        removed.beforeState=created.afterState; removed.afterState="{\"exists\":false}"; audits.saveAndFlush(removed);
        long count=audits.count();
        var html=page("storeId",Long.toString(storeId));
        assertThat(html).contains("Auditoria da jornada","Ateliê de presentes","Requisição concluída","Ação recusada",
                "Operador","Lojista","Produto sem configuração","Produto habilitado · 1 campo","Estado não registrado",
                "28/10/2026 11:13:00","Dados anteriores","Dados posteriores");
        assertThat(html.indexOf("Remoção da configuração do produto")).isLessThan(html.indexOf("Criação de campo"));
        assertThat(page()).contains("Ver jornada desta loja", "Ateliê de presentes");
        assertThat(audits.count()).isEqualTo(count);
        var detail=mvc.perform(get("/backoffice/stores/"+storeId).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("/backoffice/audit?storeId="+storeId);
        if(System.getProperty("ncf.audit.screens")!=null) {
            var dir=java.nio.file.Path.of(System.getProperty("ncf.audit.screens"));java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve("audit.html"),html);
            java.nio.file.Files.writeString(dir.resolve("audit-empty.html"),page("storeId",Long.toString(storeId),"outcome","ERROR"));
        }
    }
    @Test void combinesStoreOutcomeAndActorFiltersAndEscapesEveryStoredValue() throws Exception {
        var selected=event("<script>alert('action')</script>","ERROR","OPERATOR",0);
        selected.beforeState="<script>alert('state')</script>"; selected.route="<script>alert('route')</script>"; audits.saveAndFlush(selected);
        event("AdminController.help","COMPLETED","OPERATOR",1);
        event("AdminController.index","ERROR","MERCHANT",2);
        var other=event("AdminController.onboarding","ERROR","OPERATOR",3);other.storeId=998881002L;audits.saveAndFlush(other);
        var html=page("storeId",Long.toString(storeId),"outcome","ERROR","actor","OPERATOR");
        assertThat(html).contains("&lt;script&gt;","Ação registrada").doesNotContain("<script>","Consulta à ajuda","Acesso ao início","Acesso ao onboarding");
    }
    @SuppressWarnings("unchecked")
    @Test void paginatesFiftyEventsWithStableTieOrderingAndKeepsFilters() throws Exception {
        for(int i=0;i<61;i++) event("AdminController.help","COMPLETED","MERCHANT",0);
        var firstResult=mvc.perform(get("/backoffice/audit").session(session).param("storeId",Long.toString(storeId))
                .param("outcome","COMPLETED").param("actor","MERCHANT")).andExpect(status().isOk()).andReturn();
        var first=(Slice<UserJourneyAuditReportService.Entry>) firstResult.getModelAndView().getModel().get("events");
        var secondResult=mvc.perform(get("/backoffice/audit").session(session).param("storeId",Long.toString(storeId))
                .param("outcome","COMPLETED").param("actor","MERCHANT").param("page","1")).andExpect(status().isOk()).andReturn();
        var second=(Slice<UserJourneyAuditReportService.Entry>) secondResult.getModelAndView().getModel().get("events");
        assertThat(first.getNumberOfElements()).isEqualTo(50);assertThat(first.hasNext()).isTrue();
        assertThat(second.getNumberOfElements()).isEqualTo(11);assertThat(second.hasNext()).isFalse();
        assertThat(first.getContent().getLast().id()).isGreaterThan(second.getContent().getFirst().id());
        assertThat(firstResult.getResponse().getContentAsString()).contains("storeId="+storeId+"&amp;outcome=COMPLETED&amp;actor=MERCHANT&amp;page=1");
        assertThat(page("storeId",Long.toString(storeId),"outcome","ERROR")).contains("Nenhum evento encontrado","Página 1");
    }
}
