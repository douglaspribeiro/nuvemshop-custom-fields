package br.com.nuvemcustomfields.finance;

import br.com.nuvemcustomfields.entity.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static br.com.nuvemcustomfields.finance.FinancialEntry.*;

class SettlementRuleTest {
    static FinancialEntry payment(String resource,LocalDate day,Instant at){return new FinancialEntry(null,PaymentProviderType.EFI,"default",PaymentEnvironment.PRODUCTION,resource,"1",Stage.PAYMENT,null,123L,"sub1","BR","card","BRL",new BigDecimal("100"),null,null,null,day,day,at,"paid",Certainty.CONFIRMED,"API","Consulta de teste",Instant.now(),null,null);}
    static SettlementRule rule(int days,boolean business,SettlementRule.PayoutSchedule schedule,int payoutDay,BigDecimal minimum){return new SettlementRule("rule1",PaymentProviderType.EFI,"default",PaymentEnvironment.PRODUCTION,"BR","BRL",null,null,LocalDate.of(2026,1,1),null,"America/Sao_Paulo",LocalTime.of(16,0),days,business,true,Set.of(LocalDate.of(2026,11,20)),schedule,payoutDay,1,0,null,minimum,null,null,null,false,"Contrato da conta","operador");}
    @Test void respectsBusinessDaysHolidaysAndPaymentCutoff(){
        var r=rule(1,true,SettlementRule.PayoutSchedule.DAILY,1,null);r.validate();
        assertThat(r.releaseDate(payment("p1",LocalDate.of(2026,11,19),Instant.parse("2026-11-19T18:00:00Z")))).isEqualTo(LocalDate.of(2026,11,23));
        assertThat(r.releaseDate(payment("p2",LocalDate.of(2026,11,19),Instant.parse("2026-11-19T19:00:00Z")))).isEqualTo(LocalDate.of(2026,11,24));
    }
    @Test void noEstimateWithoutDateAndMinimumNeedsEligibleBalance(){
        var r=rule(0,false,SettlementRule.PayoutSchedule.DAILY,1,new BigDecimal("50"));
        assertThat(r.releaseDate(payment("p",null,null))).isNull();
        var p=payment("p",LocalDate.of(2026,11,17),null);
        assertThat(r.payoutDate(p,null)).isNull();assertThat(r.payoutDate(p,new BigDecimal("49"))).isNull();
        assertThat(r.payoutDate(p,new BigDecimal("60"))).isEqualTo(LocalDate.of(2026,11,19));
    }
    @Test void weeklyMonthlyFortnightlyAndManualWindows(){
        var p=payment("p",LocalDate.of(2026,11,17),null);
        assertThat(rule(0,false,SettlementRule.PayoutSchedule.WEEKLY,5,null).payoutDate(p,null)).isEqualTo(LocalDate.of(2026,11,24));
        assertThat(rule(0,false,SettlementRule.PayoutSchedule.MONTHLY,15,null).payoutDate(p,null)).isEqualTo(LocalDate.of(2026,12,16));
        assertThat(rule(0,false,SettlementRule.PayoutSchedule.FORTNIGHTLY,1,null).payoutDate(p,null)).isEqualTo(LocalDate.of(2026,12,2));
        assertThat(rule(0,false,SettlementRule.PayoutSchedule.MANUAL,1,null).payoutDate(p,null)).isNull();
    }
    @Test void explicitValidityAndSelectors(){
        var r=rule(1,true,SettlementRule.PayoutSchedule.DAILY,1,null);
        assertThat(r.matches(payment("p",LocalDate.of(2025,12,31),null))).isFalse();
        assertThat(r.matches(payment("p",LocalDate.of(2026,11,17),null))).isTrue();
    }
}
