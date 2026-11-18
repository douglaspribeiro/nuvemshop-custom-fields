package br.com.nuvemcustomfields.finance;
import br.com.nuvemcustomfields.entity.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
public class SettlementRuleForm {
    private PaymentProviderType provider = PaymentProviderType.EFI;
    public PaymentProviderType getProvider(){return provider;}
    public void setProvider(PaymentProviderType value){provider=value;}
    private String account = "default";
    public String getAccount(){return account;}
    public void setAccount(String value){account=value;}
    private PaymentEnvironment environment = PaymentEnvironment.PRODUCTION;
    public PaymentEnvironment getEnvironment(){return environment;}
    public void setEnvironment(PaymentEnvironment value){environment=value;}
    private String country;
    public String getCountry(){return country;}
    public void setCountry(String value){country=value;}
    private String currency = "BRL";
    public String getCurrency(){return currency;}
    public void setCurrency(String value){currency=value;}
    private String method;
    public String getMethod(){return method;}
    public void setMethod(String value){method=value;}
    private String installment;
    public String getInstallment(){return installment;}
    public void setInstallment(String value){installment=value;}
    private LocalDate validFrom;
    public LocalDate getValidFrom(){return validFrom;}
    public void setValidFrom(LocalDate value){validFrom=value;}
    private LocalDate validUntil;
    public LocalDate getValidUntil(){return validUntil;}
    public void setValidUntil(LocalDate value){validUntil=value;}
    private String zone = "America/Sao_Paulo";
    public String getZone(){return zone;}
    public void setZone(String value){zone=value;}
    private LocalTime cutoff;
    public LocalTime getCutoff(){return cutoff;}
    public void setCutoff(LocalTime value){cutoff=value;}
    private int releaseDays = 0;
    public int getReleaseDays(){return releaseDays;}
    public void setReleaseDays(int value){releaseDays=value;}
    private boolean businessDays = false;
    public boolean getBusinessDays(){return businessDays;}
    public void setBusinessDays(boolean value){businessDays=value;}
    private boolean rollForward = true;
    public boolean getRollForward(){return rollForward;}
    public void setRollForward(boolean value){rollForward=value;}
    private String holidays;
    public String getHolidays(){return holidays;}
    public void setHolidays(String value){holidays=value;}
    private SettlementRule.PayoutSchedule schedule = SettlementRule.PayoutSchedule.MANUAL;
    public SettlementRule.PayoutSchedule getSchedule(){return schedule;}
    public void setSchedule(SettlementRule.PayoutSchedule value){schedule=value;}
    private int payoutDay = 1;
    public int getPayoutDay(){return payoutDay;}
    public void setPayoutDay(int value){payoutDay=value;}
    private int bankDays = 0;
    public int getBankDays(){return bankDays;}
    public void setBankDays(int value){bankDays=value;}
    private int firstPayoutDays = 0;
    public int getFirstPayoutDays(){return firstPayoutDays;}
    public void setFirstPayoutDays(int value){firstPayoutDays=value;}
    private LocalDate firstPayment;
    public LocalDate getFirstPayment(){return firstPayment;}
    public void setFirstPayment(LocalDate value){firstPayment=value;}
    private BigDecimal minimum;
    public BigDecimal getMinimum(){return minimum;}
    public void setMinimum(BigDecimal value){minimum=value;}
    private BigDecimal reserve;
    public BigDecimal getReserve(){return reserve;}
    public void setReserve(BigDecimal value){reserve=value;}
    private BigDecimal estimatedFeePercent;
    public BigDecimal getEstimatedFeePercent(){return estimatedFeePercent;}
    public void setEstimatedFeePercent(BigDecimal value){estimatedFeePercent=value;}
    private BigDecimal estimatedFixedFee;
    public BigDecimal getEstimatedFixedFee(){return estimatedFixedFee;}
    public void setEstimatedFixedFee(BigDecimal value){estimatedFixedFee=value;}
    private boolean blocked = false;
    public boolean getBlocked(){return blocked;}
    public void setBlocked(boolean value){blocked=value;}
    private String evidence;
    public String getEvidence(){return evidence;}
    public void setEvidence(String value){evidence=value;}
    private String operator;
    public String getOperator(){return operator;}
    public void setOperator(String value){operator=value;}
    private SettlementRule.DateBase dateBase=SettlementRule.DateBase.PAYMENT;
    private LocalTime payoutCutoff;
    public SettlementRule.DateBase getDateBase(){return dateBase;}
    public void setDateBase(SettlementRule.DateBase value){dateBase=value;}
    public LocalTime getPayoutCutoff(){return payoutCutoff;}
    public void setPayoutCutoff(LocalTime value){payoutCutoff=value;}
    public SettlementRule rule(){return new SettlementRule(null,provider,account,environment,country,currency,method,installment,validFrom,validUntil,zone,cutoff,releaseDays,businessDays,rollForward,
            holidays==null || holidays.isBlank()?Set.of():new HashSet<>(Arrays.stream(holidays.split(",")).map(String::trim).map(LocalDate::parse).toList()),schedule,payoutDay,bankDays,firstPayoutDays,firstPayment,minimum,reserve,estimatedFeePercent,estimatedFixedFee,blocked,evidence,operator,dateBase,payoutCutoff);}
}
