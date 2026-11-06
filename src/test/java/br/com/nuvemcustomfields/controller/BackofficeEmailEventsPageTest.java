package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.service.SesEventConsumer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BackofficeEmailEventsPageTest {
    @Autowired MockMvc mvc;
    @MockitoBean SesEventConsumer consumer;
    MockHttpSession session;
    @BeforeEach void setup() {
        session=new MockHttpSession();session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);
        when(consumer.status()).thenReturn(new SesEventConsumer.RunStatus(false,"",null,null,0,0,0,0,false,null,null));
    }
    String token() throws Exception {
        var result=mvc.perform(get("/backoffice/email-events").session(session)).andExpect(status().isOk()).andReturn();
        return (String)result.getModelAndView().getModel().get("actionToken");
    }
    @Test void pageAndActionRequireBackofficeAuthentication() throws Exception {
        mvc.perform(get("/backoffice/email-events")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/backoffice/login"));
        mvc.perform(post("/backoffice/email-events/consume")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/backoffice/login"));
        verify(consumer,never()).startManual(any());
    }
    @Test void rendersButtonAndStatusWithoutConsumingOnGet() throws Exception {
        var html=mvc.perform(get("/backoffice/email-events").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Executar consumo da fila","/backoffice/email-events/consume","actionToken","a cada hora","Nenhuma execução registrada");
        verify(consumer,never()).startManual(any());
    }
    @Test void missingInvalidAndOtherSessionTokensCannotExecuteConsumption() throws Exception {
        mvc.perform(post("/backoffice/email-events/consume").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/backoffice/email-events/consume").session(session).param("actionToken","invalid")).andExpect(status().isForbidden());
        String first=token();var other=new MockHttpSession();other.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);
        mvc.perform(post("/backoffice/email-events/consume").session(other).param("actionToken",first)).andExpect(status().isForbidden());
        verify(consumer,never()).startManual(any());
    }
    @Test void validPostRequestsBackgroundExecutionAndRedirectsToResult() throws Exception {
        String token=token();when(consumer.startManual(any())).thenReturn(SesEventConsumer.ManualStart.STARTED);
        mvc.perform(post("/backoffice/email-events/consume").session(session).param("actionToken",token))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/backoffice/email-events"))
                .andExpect(flash().attribute("message","Consumo solicitado. Acompanhe o resultado nesta página."));
        verify(consumer,times(1)).startManual(any());
    }
    @Test void rendersConfigurationErrorAndRunningStateWithDisabledButton() throws Exception {
        when(consumer.configurationProblem()).thenReturn("Configure a URL completa.");
        String html=mvc.perform(get("/backoffice/email-events").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Configure a URL completa.","disabled=\"disabled\"");
        when(consumer.configurationProblem()).thenReturn(null);
        when(consumer.status()).thenReturn(new SesEventConsumer.RunStatus(true,"Manual",Instant.now(),null,2,1,1,1,false,null,null));
        html=mvc.perform(get("/backoffice/email-events").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Consumo em andamento","http-equiv=\"refresh\"","Remoções confirmadas","disabled=\"disabled\"");
    }
    @Test void rendersCompletedFailureSafelyInsteadOfReportingAnEmptyQueue() throws Exception {
        when(consumer.status()).thenReturn(new SesEventConsumer.RunStatus(false,"Manual",Instant.now(),Instant.now(),0,0,0,1,false,false,"<script>failure</script>"));
        String html=mvc.perform(get("/backoffice/email-events").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("&lt;script&gt;failure&lt;/script&gt;","sqs:GetQueueAttributes")
                .doesNotContain("<script>failure</script>","A consulta não retornou mensagens disponíveis.");
    }
}
