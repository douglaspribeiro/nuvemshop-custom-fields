package br.com.nuvemcustomfields.finance;

import br.com.nuvemcustomfields.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static br.com.nuvemcustomfields.finance.FinancialEntry.*;

/** Only allowlisted financial fields are persisted, never customer/card payloads. */
@Component
public class FinancialNormalizer {
    static String text(JsonNode n,String field){JsonNode value=n.path(field);return value.isMissingNode()||value.isNull()?null:value.asText();}
    static BigDecimal decimal(JsonNode n,String field){String v=text(n,field);return v==null?null:new BigDecimal(v);}
    static BigDecimal minor(JsonNode n,String field,String currency){BigDecimal v=decimal(n,field);return v==null?null:v.movePointLeft(Currency.getInstance(currency).getDefaultFractionDigits());}
    static Instant instant(JsonNode n,String field){JsonNode v=n.path(field);if(v.isNumber())return Instant.ofEpochSecond(v.longValue());String s=text(n,field);if(s==null||s.length()==10||s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:]+"))return null;try{return Instant.parse(s);}catch(RuntimeException ex){return OffsetDateTime.parse(s).toInstant();}}
    static LocalDate date(JsonNode n,String field){String s=text(n,field);if(s==null)return null;if(s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}.*"))return LocalDate.parse(s.substring(0,10));Instant i=instant(n,field);return i==null?null:i.atZone(ZoneOffset.UTC).toLocalDate();}
    private static String required(JsonNode n,String f){return Objects.requireNonNull(text(n,f),"Resposta sem "+f);}
    public List<FinancialEntry> payment(PaymentProviderType provider,String account,PaymentEnvironment environment,
            Long storeId,String subscription,String country,JsonNode n){
        Instant now=Instant.now();String status=required(n,"status");
        boolean paid=switch(provider){case EFI->Set.of("paid","refunded","contested","unpaid","settled").contains(status);
            case MERCADO_PAGO->Set.of("approved","refunded","charged_back","in_mediation").contains(status);
            case DODO,DODO_TEST->Set.of("succeeded","refunded").contains(status);
            case STRIPE->n.path("paid").asBoolean();
            case CREEM->Set.of("paid","refunded","partialRefund","chargedBack","chargeback").contains(status);default->false;};
        // EFI settled can be a manual confirmation and unpaid can lack a prior payment: require history evidence.
        if(provider==PaymentProviderType.EFI && Set.of("settled","unpaid").contains(status))paid=false;
        if(!paid)return List.of();
        String id=required(n,provider.isDodo()?"payment_id":provider==PaymentProviderType.EFI?"charge_id":"id");
        String currency=provider==PaymentProviderType.EFI?"BRL":required(n,provider==PaymentProviderType.MERCADO_PAGO?"currency_id":"currency").toUpperCase(Locale.ROOT);
        String method=text(n,provider==PaymentProviderType.MERCADO_PAGO?"payment_method_id":"payment_method");
        Instant paidAt=null, effective=null;LocalDate paidDate=null;BigDecimal gross=null,fee=null,tax=null,net=null,refunded=null;
        String evidence="API "+provider+" / "+id+"; status="+status;
        switch(provider){
            case EFI -> {
                gross=minor(n,n.hasNonNull("paid_value")?"paid_value":"total",currency);JsonNode payment=n.path("payment");
                if(payment.has("credit_card"))method="credit_card";else if(payment.has("banking_billet"))method="banking_billet";else if(payment.has("pix"))method="pix";
                // paid_at is used only when returned. Creation, due date and last update are not payment dates.
                paidDate=date(n,"paid_at");paidAt=instant(n,"paid_at");
                for(JsonNode h:n.path("history"))if("paid".equals(text(h,"status"))){
                    if(paidDate==null){paidDate=date(h,"created_at");paidAt=instant(h,"created_at");}}
                effective=instant(n,"updated_at");
            }
            case MERCADO_PAGO -> {
                gross=decimal(n,"transaction_amount");net=decimal(n.path("transaction_details"),"net_received_amount");tax=decimal(n,"taxes_amount");
                if(n.path("fee_details").isArray()){fee=BigDecimal.ZERO;for(JsonNode f:n.path("fee_details")){BigDecimal part=decimal(f,"amount");if(part==null){fee=null;break;}fee=fee.add(part);}}
                paidDate=date(n,"date_approved");paidAt=instant(n,"date_approved");effective=instant(n,"date_last_updated");refunded=decimal(n,"transaction_amount_refunded");
                if(n.has("live_mode") && n.path("live_mode").asBoolean()!=(environment==PaymentEnvironment.PRODUCTION))throw new IllegalArgumentException("Ambiente Mercado Pago divergente.");
            }
            case STRIPE -> {
                gross=minor(n,"amount",currency);paidAt=instant(n,"created");paidDate=date(n,"created");refunded=minor(n,"amount_refunded",currency);
                if(n.path("livemode").asBoolean()!=(environment==PaymentEnvironment.PRODUCTION))throw new IllegalArgumentException("Ambiente Stripe divergente.");
            }
            case CREEM -> {gross=minor(n,n.hasNonNull("amount_paid")?"amount_paid":"amount",currency);tax=minor(n,"tax_amount",currency);refunded=minor(n,"refunded_amount",currency);
                paidAt=instant(n,"paid_at");paidDate=date(n,"paid_at");effective=instant(n,"updated_at");
                if(n.hasNonNull("mode") && "prod".equals(text(n,"mode"))!=(environment==PaymentEnvironment.PRODUCTION))throw new IllegalArgumentException("Ambiente Creem divergente.");}
            case DODO,DODO_TEST -> {gross=minor(n,"total_amount",currency);tax=minor(n,"tax",currency);effective=instant(n,"updated_at");
                // created_at is creation, updated_at is mutable: neither proves when payment succeeded.
                paidAt=instant(n,"paid_at");paidDate=date(n,"paid_at");}
            default -> { }
        }
        FinancialEntry payment=new FinancialEntry(null,provider,account,environment,id,"1",Stage.PAYMENT,null,storeId,subscription,country,method,currency,gross,fee,tax,net,paidDate,paidDate,paidAt,status,Certainty.CONFIRMED,"API",evidence,now,effective,null);
        LocalDate postingDate=null;
        for(JsonNode l:n.path("financial_ledger"))if("payment".equals(text(l,"event_type")) && l.path("is_credit").asBoolean())postingDate=date(l,"created_at");
        payment=payment.withFinancialDates(instant(n,"captured_at"),postingDate,decimal(n,"exchange_rate"));
        payment=payment.withKey(FinancialLedger.hash(payment.identity()));var result=new ArrayList<FinancialEntry>();result.add(payment);
        if(provider==PaymentProviderType.MERCADO_PAGO) {
            LocalDate releaseDate=date(n,"money_release_date");boolean released="released".equals(text(n,"money_release_status"));
            result.add(new FinancialEntry(null,provider,account,environment,id,"1",Stage.RELEASE,payment.key(),storeId,subscription,country,method,currency,gross,fee,tax,net,releaseDate,paidDate,paidAt,released?"available":"pending",released?Certainty.CONFIRMED:releaseDate==null?Certainty.UNKNOWN:Certainty.PROVIDER,"API",evidence+"; money_release_status="+text(n,"money_release_status"),now,effective,null));
        }
        if(provider==PaymentProviderType.STRIPE && n.path("balance_transaction").isObject()) {
            JsonNode balance=n.path("balance_transaction");String settlement=required(balance,"currency").toUpperCase(Locale.ROOT);boolean available="available".equals(text(balance,"status"));
            result.add(new FinancialEntry(null,provider,account,environment,id,"1",Stage.RELEASE,payment.key(),storeId,subscription,country,method,settlement,minor(balance,"amount",settlement),minor(balance,"fee",settlement),null,minor(balance,"net",settlement),date(balance,"available_on"),paidDate,paidAt,available?"available":"pending",available?Certainty.CONFIRMED:Certainty.PROVIDER,"API",evidence+"; balance_transaction="+required(balance,"id"),now,null,null).withFinancialDates(null,null,decimal(balance,"exchange_rate")));
        }
        if(provider.isDodo() && n.path("financial_ledger").isArray()) {
            JsonNode posting=null;String settlement=text(n,"settlement_currency");
            for(JsonNode l:n.path("financial_ledger"))if("payment".equals(text(l,"event_type")) && l.path("is_credit").asBoolean())posting=l;
            if(posting!=null && settlement!=null){
                BigDecimal settled=minor(n,"settlement_amount",settlement),fees=BigDecimal.ZERO,taxes=BigDecimal.ZERO,settledNet=BigDecimal.ZERO;
                boolean complete=true,feeSeen=false;
                for(JsonNode l:n.path("financial_ledger")){
                    String event=text(l,"event_type");if(!Set.of("payment","payment_fees","tax").contains(event))continue;
                    if(!settlement.equals(text(l,"currency"))){complete=false;continue;}
                    BigDecimal value=minor(l,"amount",settlement);if(value==null){complete=false;continue;}
                    BigDecimal signed=l.path("is_credit").asBoolean()?value:value.negate();settledNet=settledNet.add(signed);
                    if("payment_fees".equals(event)){fees=fees.add(signed.negate());feeSeen=true;}if("tax".equals(event))taxes=taxes.add(signed.negate());
                }
                result.add(new FinancialEntry(null,provider,account,environment,id,"1",Stage.RELEASE,payment.key(),storeId,subscription,country,method,settlement,settled,complete&&feeSeen?fees:null,complete?taxes:null,complete&&feeSeen?settledNet:null,null,paidDate,paidAt,"ledger_posted",Certainty.PROVIDER,"API",evidence+"; liquidação registrada no ledger; saldo contábil não comprova disponibilidade para saque",now,effective,null));
            }
            for(JsonNode l:n.path("financial_ledger")) {
                String event=text(l,"event_type");if(Set.of("payment","payment_fees","tax").contains(event))continue;
                String c=text(l,"currency");BigDecimal value=c==null?null:minor(l,"amount",c);
                result.add(new FinancialEntry(null,provider,account,environment,"ledger:"+required(l,"id"),"1",Stage.ADJUSTMENT,payment.key(),storeId,subscription,country,method,c,null,null,null,value==null?null:l.path("is_credit").asBoolean()?value:value.negate(),date(l,"created_at"),null,null,event,Certainty.PROVIDER,"API",evidence+"; ledger="+required(l,"id")+"; payout="+text(l,"payout_id"),now,instant(l,"created_at"),null));
            }
        }
        if(refunded!=null && refunded.signum()>0)result.add(adjustment(payment,"refund","refund",refunded.negate(),currency,effective,evidence));
        if(Set.of("charged_back","chargedBack","chargeback","contested","in_mediation").contains(status) || n.path("disputed").asBoolean())
            result.add(adjustment(payment,"dispute","disputed",null,currency,effective,evidence+"; valor da disputa não presumido"));
        if(result.stream().noneMatch(e->e.stage()==Stage.RELEASE)) {
            result.add(new FinancialEntry(null,provider,account,environment,id,"1",Stage.RELEASE,payment.key(),storeId,subscription,country,method,currency,
                    gross,fee,tax,net,null,paidDate,paidAt,"unreported",Certainty.UNKNOWN,"API",evidence+"; disponibilidade não informada pelo provedor",now,effective,null));
        }
        return result;
    }
    private FinancialEntry adjustment(FinancialEntry p,String suffix,String status,BigDecimal amount,String currency,Instant effective,String evidence){return new FinancialEntry(null,p.provider(),p.account(),p.environment(),p.resourceId()+":"+suffix,"1",Stage.ADJUSTMENT,p.key(),p.storeId(),p.subscriptionId(),p.country(),p.method(),currency,null,null,null,amount,null,null,null,status,Certainty.PROVIDER,"API",evidence,Instant.now(),effective,null);}
    public FinancialEntry payout(PaymentProviderType provider,String account,PaymentEnvironment environment,JsonNode n){
        String currency=required(n,"currency").toUpperCase(Locale.ROOT),id=required(n,provider.isDodo()?"payout_id":"id");
        String status=required(n,"status");BigDecimal amount=minor(n,"amount",currency),fee=minor(n,"fee",currency);
        // Provider amount semantics retained. Do not subtract payout fee twice or assert a bank credit.
        return new FinancialEntry(null,provider,account,environment,id,"1",Stage.PAYOUT,null,null,null,null,text(n,"payment_method"),currency,amount,fee,null,amount,
                provider==PaymentProviderType.STRIPE?date(n,"arrival_date"):null,null,null,status,Certainty.PROVIDER,"API","API "+provider+" payout "+id+"; status do repasse não comprova crédito bancário",Instant.now(),instant(n,"updated_at"),null);
    }
}
