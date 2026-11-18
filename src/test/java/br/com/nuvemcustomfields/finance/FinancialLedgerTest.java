package br.com.nuvemcustomfields.finance;

import br.com.nuvemcustomfields.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static br.com.nuvemcustomfields.finance.FinancialEntry.*;
import static org.assertj.core.api.Assertions.*;

class FinancialLedgerTest {
    JdbcTemplate jdbc;FinancialLedger ledger;FinancialAgenda agenda;
    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V90__financial_agenda.sql")).execute(ds);
        jdbc=new JdbcTemplate(ds);ObjectMapper mapper=JsonMapper.builder().findAndAddModules().build();ledger=new FinancialLedger(jdbc,mapper);agenda=new FinancialAgenda(ledger);
    }
    FinancialEntry entry(String resource,Stage stage,String parent,String amount,Certainty certainty,String source,Instant effective){
        return new FinancialEntry(null,PaymentProviderType.EFI,"default",PaymentEnvironment.PRODUCTION,resource,"1",stage,parent,123L,"sub1","BR","card","BRL",new BigDecimal("100"),null,null,amount==null?null:new BigDecimal(amount),LocalDate.of(2026,11,17),LocalDate.of(2026,11,17),null,"pending",certainty,source,"Documento de teste",Instant.now(),effective,null);
    }
    @Test void idempotentStableIdentityAndNewRenewalPreservesBothPayments(){
        var one=ledger.record(entry("p1",Stage.PAYMENT,null,null,Certainty.CONFIRMED,"API",null),"test");
        ledger.record(entry("p1",Stage.PAYMENT,null,null,Certainty.CONFIRMED,"API",null),"test");
        ledger.record(entry("p2",Stage.PAYMENT,null,null,Certainty.CONFIRMED,"API",null),"test");
        assertThat(ledger.entries()).hasSize(2);assertThat(ledger.require(one.key()).resourceId()).isEqualTo("p1");
    }
    @Test void authoritativeProviderReplacesEstimateAndLateEventDoesNotRegress(){
        var pay=ledger.record(entry("p1",Stage.PAYMENT,null,null,Certainty.CONFIRMED,"API",null),"test");
        var estimate=ledger.record(entry("p1",Stage.RELEASE,pay.key(),"98",Certainty.ESTIMATED,"CALCULATION",null),"test");
        var current=ledger.record(entry("p1",Stage.RELEASE,pay.key(),"97",Certainty.PROVIDER,"API",Instant.parse("2026-11-17T12:00:00Z")),"test");
        ledger.record(entry("p1",Stage.RELEASE,pay.key(),"96",Certainty.PROVIDER,"API",Instant.parse("2026-11-16T12:00:00Z")),"test");
        ledger.record(estimate,"test");assertThat(ledger.require(current.key()).net()).isEqualByComparingTo("97");assertThat(ledger.history(current.key())).hasSize(2);
    }
    @Test void stagesNeverDoubleCountAndUnknownNetNeverBecomesZero(){
        var p=ledger.record(entry("p",Stage.PAYMENT,null,null,Certainty.CONFIRMED,"API",null),"test");
        ledger.record(entry("p",Stage.RELEASE,p.key(),null,Certainty.PROVIDER,"API",null),"test");
        var result=agenda.query(new FinancialAgenda.Filter(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30),Stage.RELEASE,null,null,null,null,null,null,null));
        assertThat(result.totals()).hasSize(1);assertThat(result.totals().getFirst().net()).isNull();assertThat(result.totals().getFirst().unknownNet()).isEqualTo(1);
    }
    @Test void aggregatePayoutAndPartialReleasesRespectAllocationLimits(){
        var p=ledger.record(entry("p",Stage.PAYMENT,null,null,Certainty.CONFIRMED,"API",null),"test");
        var r=ledger.record(entry("p",Stage.RELEASE,p.key(),"97",Certainty.PROVIDER,"API",null),"test");
        var payout=ledger.record(entry("po",Stage.PAYOUT,null,"100",Certainty.PROVIDER,"API",null),"test");
        ledger.allocate(payout.key(),r.key(),new BigDecimal("50"),"Extrato","test");ledger.allocate(payout.key(),r.key(),new BigDecimal("50"),"Extrato","test");
        assertThat(ledger.links(payout.key())).hasSize(1);
        assertThatThrownBy(()->ledger.allocate(payout.key(),r.key(),new BigDecimal("98"),"Extrato","test")).isInstanceOf(IllegalArgumentException.class);
        var second=ledger.record(entry("po2",Stage.PAYOUT,null,"100",Certainty.PROVIDER,"API",null),"test");
        assertThatThrownBy(()->ledger.allocate(second.key(),r.key(),new BigDecimal("48"),"Extrato","test")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void immutableRuleVersionAndEstimateSimulationPreserveAuthoritativeDates(){
        var p=ledger.record(entry("p",Stage.PAYMENT,null,null,Certainty.CONFIRMED,"API",null),"test");
        var rule=ledger.saveRule(SettlementRuleTest.rule(1,true,SettlementRule.PayoutSchedule.MANUAL,1,null));
        ledger.estimate(p.key(),rule.id(),"test",false);
        var r=ledger.entries().stream().filter(e->e.stage()==Stage.RELEASE).findFirst().orElseThrow();
        ledger.record(entry("p",Stage.RELEASE,p.key(),"98",Certainty.PROVIDER,"API",Instant.now()),"test");
        ledger.estimate(p.key(),rule.id(),"test",true);
        assertThat(ledger.require(r.key()).certainty()).isEqualTo(Certainty.PROVIDER);assertThat(ledger.rules()).hasSize(1);
    }
    @Test void csvQuotedFieldsDuplicateDetectionAndFormulaInjectionProtection(){
        var importer=new FinancialImport(ledger);
        assertThat(FinancialImport.parse("a,b\r\n\"one, two\",\"line\nline\"\r\n").get(1)).containsExactly("one, two","line\nline");
        assertThatThrownBy(()->FinancialImport.parse("\"broken")).isInstanceOf(IllegalArgumentException.class);
        var p=ledger.record(entry("p",Stage.PAYMENT,null,null,Certainty.CONFIRMED,"API",null),"test");
        String csv=FinancialImport.HEADER+"\nEFI,default,PRODUCTION,PAYMENT,p1,1,,123,sub1,BR,card,BRL,100,,,,2026-11-17,2026-11-17,,paid,CONFIRMED,relatorio,\n";
        assertThat(importer.preview(csv,"Extrato")).hasSize(1);importer.commit(importer.preview(csv,"Extrato"),"test");
        assertThat(ledger.entries()).hasSize(2);
        assertThatThrownBy(()->importer.preview(csv+csv.substring(csv.indexOf('\n')+1),"Extrato")).isInstanceOf(IllegalArgumentException.class);
        assertThat(agenda.csv(new FinancialAgenda.Filter(null,null,Stage.PAYMENT,null,null,null,null,null,null,null))).contains("CONFIRMED");
    }
    @Test void providerNetAndEstimatedDateHaveIndependentCertaintyAndDoNotOverwriteFinancialFacts(){
        var p=ledger.record(entry("p",Stage.PAYMENT,null,null,Certainty.CONFIRMED,"API",null),"test");
        var release=ledger.record(entry("p",Stage.RELEASE,p.key(),"97",Certainty.PROVIDER,"API",null).withForecastDate(null,null),"test");
        // Test starts with no provider date, then applies an account rule without altering the net amount.
        var noDate=new FinancialEntry(null,release.provider(),release.account(),release.environment(),release.resourceId(),release.installment(),release.stage(),release.parentKey(),release.storeId(),release.subscriptionId(),release.country(),release.method(),release.currency(),release.gross(),release.fee(),release.tax(),release.net(),null,release.paidDate(),release.paidAt(),release.status(),release.certainty(),release.source(),release.evidence(),Instant.now(),null,null);
        ledger.record(noDate,"test");
        var rule=ledger.saveRule(SettlementRuleTest.rule(2,true,SettlementRule.PayoutSchedule.MANUAL,1,null));
        ledger.estimate(p.key(),rule.id(),"test",false);
        var forecast=ledger.require(release.key());assertThat(forecast.net()).isEqualByComparingTo("97");
        assertThat(forecast.dateCertainty()).isEqualTo(Certainty.ESTIMATED);assertThat(forecast.amountCertainty()).isEqualTo(Certainty.PROVIDER);
        ledger.record(noDate,"test");assertThat(ledger.require(release.key()).date()).isEqualTo(forecast.date());
    }
    @Test void refundsAdjustOnlyTheRelatedReleaseAndPreserveOriginalProviderNet(){
        var p=ledger.record(entry("p",Stage.PAYMENT,null,null,Certainty.CONFIRMED,"API",null),"test");
        var release=ledger.record(entry("p",Stage.RELEASE,p.key(),"97",Certainty.PROVIDER,"API",null),"test");
        var adjustment=new FinancialEntry(null,PaymentProviderType.EFI,"default",PaymentEnvironment.PRODUCTION,"refund1","1",Stage.ADJUSTMENT,p.key(),123L,"sub1","BR","card","BRL",null,null,null,new BigDecimal("-20"),null,null,null,"refund",Certainty.CONFIRMED,"REPORT","Extrato",Instant.now(),null,null);
        ledger.record(adjustment,"test");
        var result=agenda.query(new FinancialAgenda.Filter(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30),Stage.RELEASE,null,null,null,null,null,null,null));
        assertThat(result.totals().getFirst().net()).isEqualByComparingTo("77");assertThat(ledger.require(release.key()).net()).isEqualByComparingTo("97");
    }
    @Test void sandboxIsExcludedByDefaultAndEachCurrencyHasItsOwnTotal(){
        ledger.record(entry("p",Stage.PAYMENT,null,"99",Certainty.CONFIRMED,"API",null),"test");
        var other=new FinancialEntry(null,PaymentProviderType.DODO_TEST,"default",PaymentEnvironment.SANDBOX,"test_payment","1",Stage.PAYMENT,null,1L,"sub","MX","card","USD",new BigDecimal("50"),null,null,null,LocalDate.of(2026,11,17),LocalDate.of(2026,11,17),null,"paid",Certainty.CONFIRMED,"API","Teste",Instant.now(),null,null);
        ledger.record(other,"test");
        var result=agenda.query(new FinancialAgenda.Filter(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30),Stage.PAYMENT,null,null,null,null,null,null,null));
        assertThat(result.entries()).hasSize(1);assertThat(result.totals()).extracting(FinancialAgenda.Total::currency).containsExactly("BRL");
    }

    @Test void manualReportFillsMissingPaymentDateWithoutOverwritingProviderEvidence(){
        var api=new FinancialEntry(null,PaymentProviderType.EFI,"default",PaymentEnvironment.PRODUCTION,"p_gap","1",Stage.PAYMENT,null,123L,"sub1","BR","card","BRL",new BigDecimal("100"),null,null,null,null,null,null,"paid",Certainty.CONFIRMED,"API","API sem data",Instant.now(),null,null);
        var saved=ledger.record(api,"test");
        var report=new FinancialEntry(null,PaymentProviderType.EFI,"default",PaymentEnvironment.PRODUCTION,"p_gap","1",Stage.PAYMENT,null,123L,"sub1","BR","card","BRL",new BigDecimal("100"),null,null,null,LocalDate.of(2026,11,17),LocalDate.of(2026,11,17),null,"paid",Certainty.CONFIRMED,"REPORT","Comprovante pago em 17/11",Instant.now(),null,null);
        ledger.record(report,"test");assertThat(ledger.require(saved.key()).paidDate()).isNull();
        var effective=FinancialAgenda.project(ledger.require(saved.key()),ledger.entries());assertThat(effective.paidDate()).isEqualTo(LocalDate.of(2026,11,17));
        var rule=ledger.saveRule(SettlementRuleTest.rule(2,true,SettlementRule.PayoutSchedule.MANUAL,1,null));
        ledger.estimate(saved.key(),rule.id(),"test",false);
        assertThat(ledger.entries().stream().filter(e->e.stage()==Stage.RELEASE)).hasSize(1);
        assertThat(ledger.history(saved.key())).extracting(h->h.get("action")).contains("REPORT_DISCREPANCY");
    }

    @Test void documentedReleaseFillsApiGapAndIsPreservedOnTheNextMissingApiSnapshot()throws Exception{
        var parser=new FinancialNormalizer();var mapper=new ObjectMapper();
        var data=mapper.readTree("{\"charge_id\":1,\"status\":\"paid\",\"paid_value\":1999,\"updated_at\":\"2026-11-17T12:00:00Z\"}");
        var rows=parser.payment(PaymentProviderType.EFI,"default",PaymentEnvironment.PRODUCTION,123L,"sub1","BR",data);
        for(var e:rows)ledger.record(e,"sync");var gap=ledger.entries().stream().filter(e->e.stage()==Stage.RELEASE).findFirst().orElseThrow();
        var report=new FinancialEntry(null,gap.provider(),gap.account(),gap.environment(),gap.resourceId(),gap.installment(),gap.stage(),gap.parentKey(),gap.storeId(),gap.subscriptionId(),gap.country(),gap.method(),gap.currency(),gap.gross(),new BigDecimal("1"),BigDecimal.ZERO,new BigDecimal("18.99"),LocalDate.of(2026,12,17),null,null,"pending",Certainty.PROVIDER,"REPORT","Agenda da conta",Instant.now(),null,null);
        ledger.record(report,"operator");for(var e:parser.payment(PaymentProviderType.EFI,"default",PaymentEnvironment.PRODUCTION,123L,"sub1","BR",data))ledger.record(e,"sync");
        assertThat(ledger.require(gap.key()).date()).isEqualTo(LocalDate.of(2026,12,17));assertThat(ledger.require(gap.key()).net()).isEqualByComparingTo("18.99");assertThat(ledger.require(gap.key()).source()).isEqualTo("REPORT");
    }

}
