package br.com.nuvemcustomfields.finance;
import br.com.nuvemcustomfields.entity.*;
import java.math.BigDecimal;
import java.time.*;
/** Amount, date and lifecycle evidence have independent certainty. */
public record FinancialEntry(String key,
        PaymentProviderType provider,
        String account,
        PaymentEnvironment environment,
        String resourceId,
        String installment,
        Stage stage,
        String parentKey,
        Long storeId,
        String subscriptionId,
        String country,
        String method,
        String currency,
        BigDecimal gross,
        BigDecimal fee,
        BigDecimal tax,
        BigDecimal net,
        LocalDate date,
        LocalDate paidDate,
        Instant paidAt,
        String status,
        Certainty certainty,
        String source,
        String evidence,
        Instant observedAt,
        Instant effectiveAt,
        String ruleId,
        Instant capturedAt,
        LocalDate postedDate,
        BigDecimal exchangeRate,
        Certainty dateCertainty,
        Certainty amountCertainty) {
    public enum Stage { PAYMENT, RELEASE, PAYOUT, BANK, ADJUSTMENT }
    public enum Certainty { CONFIRMED, PROVIDER, ESTIMATED, UNKNOWN }
    public FinancialEntry(String key,PaymentProviderType provider,String account,PaymentEnvironment environment,String resourceId,String installment,Stage stage,String parentKey,Long storeId,String subscriptionId,String country,String method,String currency,BigDecimal gross,BigDecimal fee,BigDecimal tax,BigDecimal net,LocalDate date,LocalDate paidDate,Instant paidAt,String status,Certainty certainty,String source,String evidence,Instant observedAt,Instant effectiveAt,String ruleId) {
        this(key,provider,account,environment,resourceId,installment,stage,parentKey,storeId,subscriptionId,country,method,currency,gross,fee,tax,net,date,paidDate,paidAt,status,certainty,source,evidence,observedAt,effectiveAt,ruleId,null,null,null);
    }
    public FinancialEntry(String key,PaymentProviderType provider,String account,PaymentEnvironment environment,String resourceId,String installment,Stage stage,String parentKey,Long storeId,String subscriptionId,String country,String method,String currency,BigDecimal gross,BigDecimal fee,BigDecimal tax,BigDecimal net,LocalDate date,LocalDate paidDate,Instant paidAt,String status,Certainty certainty,String source,String evidence,Instant observedAt,Instant effectiveAt,String ruleId,Instant capturedAt,LocalDate postedDate,BigDecimal exchangeRate) {
        this(key,provider,account,environment,resourceId,installment,stage,parentKey,storeId,subscriptionId,country,method,currency,gross,fee,tax,net,date,paidDate,paidAt,status,certainty,source,evidence,observedAt,effectiveAt,ruleId,capturedAt,postedDate,exchangeRate,date==null?Certainty.UNKNOWN:certainty,gross==null&&net==null?Certainty.UNKNOWN:certainty);
    }
    public FinancialEntry withKey(String value){return new FinancialEntry(value,provider,account,environment,resourceId,installment,stage,parentKey,storeId,subscriptionId,country,method,currency,gross,fee,tax,net,date,paidDate,paidAt,status,certainty,source,evidence,observedAt,effectiveAt,ruleId,capturedAt,postedDate,exchangeRate,dateCertainty,amountCertainty);}
    public FinancialEntry withFinancialDates(Instant capture,LocalDate posting,BigDecimal rate){return new FinancialEntry(key,provider,account,environment,resourceId,installment,stage,parentKey,storeId,subscriptionId,country,method,currency,gross,fee,tax,net,date,paidDate,paidAt,status,certainty,source,evidence,observedAt,effectiveAt,ruleId,capture,posting,rate,dateCertainty,amountCertainty);}
    public FinancialEntry withForecastDate(LocalDate value,String version){return new FinancialEntry(key,provider,account,environment,resourceId,installment,stage,parentKey,storeId,subscriptionId,country,method,currency,gross,fee,tax,net,value,paidDate,paidAt,status,value==null?certainty:Certainty.ESTIMATED,source,evidence,observedAt,effectiveAt,version,capturedAt,postedDate,exchangeRate,value==null?Certainty.UNKNOWN:Certainty.ESTIMATED,amountCertainty);}
    public FinancialEntry withAdjustedNet(BigDecimal value){return new FinancialEntry(key,provider,account,environment,resourceId,installment,stage,parentKey,storeId,subscriptionId,country,method,currency,gross,fee,tax,value,date,paidDate,paidAt,status,certainty,source,evidence,observedAt,effectiveAt,ruleId,capturedAt,postedDate,exchangeRate,dateCertainty,amountCertainty);}
    public FinancialEntry withManualDate(LocalDate value){return new FinancialEntry(key,provider,account,environment,resourceId,installment,stage,parentKey,storeId,subscriptionId,country,method,currency,gross,fee,tax,net,value,paidDate,paidAt,status,certainty,source,evidence,observedAt,effectiveAt,ruleId,capturedAt,postedDate,exchangeRate,Certainty.CONFIRMED,amountCertainty);}
    public FinancialEntry withEstimatedAmounts(BigDecimal estimatedFee,BigDecimal estimatedNet){
        return new FinancialEntry(key,provider,account,environment,resourceId,installment,stage,parentKey,storeId,subscriptionId,country,method,currency,gross,estimatedFee,tax,estimatedNet,date,paidDate,paidAt,status,Certainty.ESTIMATED,source,evidence,observedAt,effectiveAt,ruleId,capturedAt,postedDate,exchangeRate,dateCertainty,Certainty.ESTIMATED);
    }
    public FinancialEntry withDocumentedPaymentDate(LocalDate value){
        return new FinancialEntry(key,provider,account,environment,resourceId,installment,stage,parentKey,storeId,subscriptionId,country,method,currency,gross,fee,tax,net,value,value,paidAt,status,certainty,source,evidence,observedAt,effectiveAt,ruleId,capturedAt,postedDate,exchangeRate,Certainty.CONFIRMED,amountCertainty);
    }
    public String identity(){return provider+":"+account+":"+environment+":"+stage+":"+resourceId+":"+installment;}
    public BigDecimal amount(){return stage==Stage.PAYMENT?gross:net;}
    public boolean overdue(){return date!=null && date.isBefore(LocalDate.now(ZoneId.of("America/Sao_Paulo")))
            && dateCertainty!=Certainty.CONFIRMED && !java.util.Set.of("failed","returned","refunded","cancelled").contains(status);}
    public boolean stale(){return observedAt!=null && observedAt.isBefore(Instant.now().minusSeconds(172800));}
}
