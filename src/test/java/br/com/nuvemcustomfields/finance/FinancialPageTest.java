package br.com.nuvemcustomfields.finance;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import static br.com.nuvemcustomfields.finance.FinancialEntry.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"payments.finance.enabled=false","payments.finance.initial-delay-ms=3600000"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinancialPageTest {
    @Autowired MockMvc mvc;@Autowired DataSource ds;@Autowired JdbcTemplate jdbc;@Autowired FinancialLedger ledger;
    @BeforeAll void schema(){new ResourceDatabasePopulator(new ClassPathResource("db/migration/V90__financial_agenda.sql")).execute(ds);}
    @BeforeEach void reset(){jdbc.update("delete from financial_links");jdbc.update("delete from financial_audit");jdbc.update("delete from financial_entries");jdbc.update("delete from financial_rules");}
    MockHttpSession admin(){var s=new MockHttpSession();s.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);return s;}
    FinancialEntry payment(){return ledger.record(SettlementRuleTest.payment("p1",LocalDate.of(2026,11,17),null),"test");}
    @Test void rendersAgendaCalendarListDetailAndVersionsWithoutBrowser()throws Exception{
        var s=admin();var p=payment();ledger.saveRule(SettlementRuleTest.rule(2,true,SettlementRule.PayoutSchedule.MANUAL,1,null));
        mvc.perform(get("/backoffice/finance").session(s)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Sem previsão")));
        mvc.perform(get("/backoffice/finance").param("view","week").param("stage","PAYMENT").session(s)).andExpect(status().isOk());
        mvc.perform(get("/backoffice/finance/"+p.key()).session(s)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Histórico auditável")));
        mvc.perform(get("/backoffice/finance/export.csv").param("stage","PAYMENT").session(s)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("p1")));
    }
    @Test void importRequiresOperatorAuthenticationCsrfAndReviewThenCommitsOnce()throws Exception{
        mvc.perform(get("/backoffice/finance")).andExpect(status().is3xxRedirection());
        var s=admin();mvc.perform(get("/backoffice/finance").session(s)).andExpect(status().isOk());
        mvc.perform(post("/backoffice/finance/import/commit").session(s).param("reviewToken",String.valueOf(s.getAttribute("financeImport"))).param("csrf","wrong")).andExpect(status().isForbidden());
        String csv=FinancialImport.HEADER+"\nEFI,default,PRODUCTION,PAYMENT,p2,1,,123,sub1,BR,card,BRL,100,,,,2026-11-17,2026-11-17,,paid,CONFIRMED,relatorio,\n";
        var file=new MockMultipartFile("file","report.csv","text/csv",csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/backoffice/finance/import/preview").file(file).session(s).param("csrf",(String)s.getAttribute("financeCsrf")).param("operator","test").param("evidence","Extrato")).andExpect(status().isOk());
        assertThat(ledger.entries()).isEmpty();assertThat(s.getAttribute("financeImport")).isInstanceOf(String.class);assertThat(((String)s.getAttribute("financeImport")).length()).isLessThan(100);
        mvc.perform(post("/backoffice/finance/import/commit").session(s).param("reviewToken",String.valueOf(s.getAttribute("financeImport"))).param("csrf",(String)s.getAttribute("financeCsrf"))).andExpect(status().is3xxRedirection());
        assertThat(ledger.entries()).hasSize(1);
        mvc.perform(post("/backoffice/finance/import/commit").session(s).param("reviewToken",String.valueOf(s.getAttribute("financeImport"))).param("csrf",(String)s.getAttribute("financeCsrf"))).andExpect(status().isBadRequest());
    }
    @Test void bankCreditIsDocumentedAndDoesNotFollowProviderSuccessAutomatically()throws Exception{
        var s=admin();mvc.perform(get("/backoffice/finance").session(s));
        var payout=ledger.record(new FinancialEntry(null,PaymentProviderType.EFI,"default",PaymentEnvironment.PRODUCTION,"po","1",Stage.PAYOUT,null,null,null,null,null,"BRL",null,null,null,new BigDecimal("100"),null,null,null,"paid",Certainty.PROVIDER,"REPORT","Extrato gateway",Instant.now(),null,null),"test");
        mvc.perform(get("/backoffice/finance/"+payout.key()).session(s)).andExpect(status().isOk());
        assertThat(ledger.entries()).noneMatch(e->e.stage()==Stage.BANK);
        mvc.perform(post("/backoffice/finance/"+payout.key()+"/bank").session(s).param("csrf",(String)s.getAttribute("financeCsrf")).param("reference","bank1").param("date","2026-11-17").param("amount","60").param("evidence","Extrato bancário").param("operator","test")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/backoffice/finance/"+payout.key()+"/bank").session(s).param("csrf",(String)s.getAttribute("financeCsrf")).param("reference","bank2").param("date","2026-11-17").param("amount","50").param("evidence","Extrato bancário").param("operator","test")).andExpect(status().isBadRequest());
        assertThat(ledger.entries().stream().filter(e->e.stage()==Stage.BANK)).hasSize(1);
    }
    @Test void simulationPrecedesRuleSaveAndExplicitRecalculation()throws Exception{
        var s=admin();var p=payment();mvc.perform(get("/backoffice/finance").session(s));String token=(String)s.getAttribute("financeCsrf");
        mvc.perform(post("/backoffice/finance/rules/preview").session(s).param("csrf",token).param("provider","EFI").param("account","default").param("environment","PRODUCTION").param("currency","BRL").param("validFrom","2026-01-01").param("releaseDays","2").param("evidence","Contrato").param("operator","test").param("paymentKey",p.key())).andExpect(status().isOk());
        assertThat(ledger.rules()).isEmpty();
        mvc.perform(post("/backoffice/finance/rules/save").session(s).param("reviewToken",String.valueOf(s.getAttribute("financeRuleToken"))).param("csrf",token)).andExpect(status().is3xxRedirection());
        assertThat(ledger.rules()).hasSize(1);
        mvc.perform(post("/backoffice/finance/estimates/preview").session(s).param("csrf",token).param("paymentKey",p.key()).param("ruleId",ledger.rules().getFirst().id()).param("operator","test")).andExpect(status().isOk());
        mvc.perform(post("/backoffice/finance/estimates/apply").session(s).param("reviewToken",String.valueOf(s.getAttribute("financeEstimateKeys"))).param("csrf",token)).andExpect(status().is3xxRedirection());
        assertThat(ledger.entries()).hasSize(3);
    }
    @Test void invalidHistoricalImportRollsBackAllRows()throws Exception{
        var s=admin();mvc.perform(get("/backoffice/finance").session(s));String token=(String)s.getAttribute("financeCsrf");
        String csv=FinancialImport.HEADER+"\nEFI,default,PRODUCTION,PAYMENT,pvalid,1,,123,sub1,BR,card,BRL,100,,,,2026-11-17,2026-11-17,,paid,CONFIRMED,relatorio,\n"
                +"EFI,default,PRODUCTION,RELEASE,rinvalid,1,missing,123,sub1,BR,card,BRL,100,,,98,2026-11-17,2026-11-17,,pending,PROVIDER,relatorio,\n";
        var file=new MockMultipartFile("file","report.csv","text/csv",csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/backoffice/finance/import/preview").file(file).session(s).param("csrf",token).param("operator","test").param("evidence","Extrato")).andExpect(status().isOk());
        mvc.perform(post("/backoffice/finance/import/commit").session(s).param("reviewToken",String.valueOf(s.getAttribute("financeImport"))).param("csrf",token)).andExpect(status().isBadRequest());
        assertThat(ledger.entries()).isEmpty();
    }

}
