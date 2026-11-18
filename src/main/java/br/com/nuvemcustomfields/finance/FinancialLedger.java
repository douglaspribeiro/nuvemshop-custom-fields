package br.com.nuvemcustomfields.finance;

import br.com.nuvemcustomfields.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static br.com.nuvemcustomfields.finance.FinancialEntry.*;

@Service
public class FinancialLedger {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public FinancialLedger(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    public String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception ex){throw new IllegalArgumentException("Dados financeiros inválidos.",ex);}}
    public <T> T read(String value,Class<T> type){try{return mapper.readValue(value,type);}catch(Exception ex){throw new IllegalArgumentException("Registro financeiro inválido.",ex);}}
    public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    public FinancialEntry require(String key){return jdbc.query("select document from financial_entries where entry_key=?",(r,n)->read(r.getString(1),FinancialEntry.class),key)
            .stream().findFirst().orElseThrow(()->new IllegalArgumentException("Lançamento não encontrado."));}
    public List<FinancialEntry> entries(){return jdbc.query("select document from financial_entries order by entry_date,entry_key",(r,n)->read(r.getString(1),FinancialEntry.class));}
    public String storeDraft(String kind,Object document){
        jdbc.update("delete from financial_drafts where expires_at<?",Timestamp.from(Instant.now()));
        String id=UUID.randomUUID().toString();jdbc.update("insert into financial_drafts(draft_id,draft_kind,document,expires_at) values(?,?,?,?)",id,kind,json(document),Timestamp.from(Instant.now().plusSeconds(1800)));return id;
    }
    public String draft(String id,String kind){return jdbc.query("select document from financial_drafts where draft_id=? and draft_kind=? and expires_at>?",(r,n)->r.getString(1),id,kind,Timestamp.from(Instant.now())).stream().findFirst().orElseThrow(()->new IllegalArgumentException("Prévia expirada; envie novamente."));}
    public void deleteDraft(String id){jdbc.update("delete from financial_drafts where draft_id=?",id);}
    public List<Map<String,Object>> history(String key){return jdbc.queryForList("select action,operator_name,evidence,before_document,after_document,created_at from financial_audit where entry_key=? order by id desc",key);}
    public List<Map<String,Object>> links(String key){return jdbc.queryForList("select * from financial_links where payout_key=? or release_key=?",key,key);}
    public List<Map<String,Object>> coverage(){return jdbc.queryForList("select * from financial_sync order by provider,account_key,environment,scope_key");}
    public List<SettlementRule> rules(){return jdbc.query("select document from financial_rules order by created_at desc,rule_id",(r,n)->read(r.getString(1),SettlementRule.class));}
    public static void validate(FinancialEntry e){
        if(e.provider()==null || e.environment()==null || e.stage()==null || e.certainty()==null || e.dateCertainty()==null || e.amountCertainty()==null || e.account()==null
                || !e.account().matches("[A-Za-z0-9_.-]{1,80}") || e.resourceId()==null || !e.resourceId().matches("[A-Za-z0-9_.:-]{1,160}")
                || e.installment()==null || !e.installment().matches("[A-Za-z0-9_.-]{1,32}") || e.observedAt()==null
                || e.source()==null || !Set.of("API","WEBHOOK","REPORT","MANUAL","CALCULATION").contains(e.source())
                || e.evidence()==null || e.evidence().isBlank() || e.evidence().length()>2000
                || e.status()==null || !e.status().matches("[a-zA-Z0-9_.-]{1,60}")) throw new IllegalArgumentException("Identidade, fonte e evidência são obrigatórias.");
        if(e.currency()!=null) java.util.Currency.getInstance(e.currency());
        if(e.exchangeRate()!=null && e.exchangeRate().signum()<=0)throw new IllegalArgumentException("Câmbio comprovado deve ser positivo.");
        if(e.stage()==Stage.BANK && e.net()!=null && e.net().signum()<=0)throw new IllegalArgumentException("Crédito bancário deve ser positivo.");
        if(e.stage()!=Stage.ADJUSTMENT) for(var v:new BigDecimal[]{e.gross(),e.fee(),e.tax(),e.net()})
            if(v!=null && v.signum()<0)throw new IllegalArgumentException("Use um ajuste para valores negativos.");
        for(var v:new BigDecimal[]{e.gross(),e.fee(),e.tax(),e.net()})
            if(v!=null && (v.precision()>19 || v.scale()>6 || e.currency()==null))throw new IllegalArgumentException("Valor ou moeda inválidos.");
        if(e.provider()==PaymentProviderType.DODO_TEST && e.environment()!=PaymentEnvironment.SANDBOX)
            throw new IllegalArgumentException("Dodo Test pertence ao sandbox.");
        if(e.stage()==Stage.PAYMENT && e.certainty()==Certainty.ESTIMATED)throw new IllegalArgumentException("Recebível exige pagamento comprovado.");
        if(e.stage()==Stage.BANK && (!Set.of("MANUAL","REPORT").contains(e.source()) || e.certainty()!=Certainty.CONFIRMED || e.date()==null || e.net()==null || e.parentKey()==null))
            throw new IllegalArgumentException("Crédito bancário exige conciliação documentada, data, valor e repasse.");
    }
    @Transactional
    public FinancialEntry record(FinancialEntry input,String operator){
        validate(input); if(operator==null || operator.isBlank() || operator.length()>120)throw new IllegalArgumentException("Responsável obrigatório.");
        String key=hash(input.identity()); FinancialEntry value=input.withKey(key);
        var existing=jdbc.query("select document,version from financial_entries where entry_key=? for update",(r,n)->Map.entry(read(r.getString(1),FinancialEntry.class),r.getLong(2)),key);
        FinancialEntry before=existing.isEmpty()?null:existing.getFirst().getKey();
        if(value.parentKey()!=null){
            if(key.equals(value.parentKey()))throw new IllegalArgumentException("Um lançamento não pode ser sua própria origem.");jdbc.queryForList("select entry_key from financial_entries where entry_key=? for update",value.parentKey());
            FinancialEntry parent=require(value.parentKey());
            if(parent.stage()==Stage.ADJUSTMENT)throw new IllegalArgumentException("Relacione o ajuste ao movimento original.");
            if(parent.provider()!=value.provider() || !parent.account().equals(value.account()) || parent.environment()!=value.environment())throw new IllegalArgumentException("Origem pertence a outra conta/ambiente.");
            if(value.stage()==Stage.RELEASE && parent.stage()!=Stage.PAYMENT || value.stage()==Stage.BANK && parent.stage()!=Stage.PAYOUT)
                throw new IllegalArgumentException("Etapas relacionadas inválidas.");
            if(value.stage()==Stage.BANK) {
                if(parent.certainty()==Certainty.ESTIMATED)throw new IllegalArgumentException("Concilie um repasse real, não uma previsão.");
                java.math.BigDecimal credited=entries().stream().filter(e->e.stage()==Stage.BANK && parent.key().equals(e.parentKey()) && !e.key().equals(key)).map(FinancialEntry::net).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
                if(parent.net()!=null && value.net().add(credited).compareTo(parent.net())>0)throw new IllegalArgumentException("Créditos acumulados excedem o repasse.");
            }
            if(value.stage()==Stage.BANK && (!Objects.equals(parent.currency(),value.currency()) || parent.net()==null || value.net().compareTo(parent.net())>0))
                throw new IllegalArgumentException("Conciliação excede o repasse ou exige moeda de destino documentada no repasse.");
        }
        if(before!=null){
            if("unreported".equals(value.status()) && Set.of("REPORT","MANUAL").contains(before.source()))return before;
            if(value.stage()==Stage.RELEASE && value.net()==null && value.fee()==null && before.amountCertainty()==Certainty.ESTIMATED
                    && Objects.equals(value.gross(),before.gross()) && Objects.equals(value.tax(),before.tax()) && Objects.equals(value.currency(),before.currency()))
                value=value.withEstimatedAmounts(before.fee(),before.net());
            if(value.date()==null && before.dateCertainty()==Certainty.ESTIMATED && value.stage()==Stage.RELEASE)
                value=value.withForecastDate(before.date(),before.ruleId());
            if(before.equals(value))return before;
            var beforeTree=mapper.valueToTree(before);var valueTree=mapper.valueToTree(value);
            ((com.fasterxml.jackson.databind.node.ObjectNode)beforeTree).remove("observedAt");((com.fasterxml.jackson.databind.node.ObjectNode)valueTree).remove("observedAt");
            if(beforeTree.equals(valueTree)) {
                jdbc.update("update financial_entries set document=?,observed_at=?,updated_at=? where entry_key=?",json(value),Timestamp.from(value.observedAt()),Timestamp.from(Instant.now()),key);
                return value;
            }
            if(before.stage()==Stage.BANK && !before.equals(value)) {
                var oldTree=mapper.valueToTree(before);var newTree=mapper.valueToTree(value);
                ((com.fasterxml.jackson.databind.node.ObjectNode)oldTree).remove("observedAt");((com.fasterxml.jackson.databind.node.ObjectNode)newTree).remove("observedAt");
                if(!oldTree.equals(newTree))throw new IllegalArgumentException("Crédito já conciliado; registre ajuste documentado para correção.");
            }
            // Provider facts are preserved. Manual discrepancies are separate adjustment entries.
            if(!before.source().equals(value.source()) && rank(value.source())<rank(before.source())
                    && !(before.stage()==Stage.RELEASE && "unreported".equals(before.status()) && "REPORT".equals(value.source()))){
                if("REPORT".equals(value.source()) && value.stage()==Stage.PAYMENT && before.paidDate()==null && value.paidDate()!=null){
                    record(new FinancialEntry(null,before.provider(),before.account(),before.environment(),"payment-date:"+hash(before.resourceId()).substring(0,40),before.installment(),Stage.ADJUSTMENT,before.key(),before.storeId(),before.subscriptionId(),before.country(),before.method(),before.currency(),null,null,null,null,value.paidDate(),null,null,"date_override",Certainty.CONFIRMED,"REPORT",value.evidence(),value.observedAt(),null,null),operator);
                }
                if("REPORT".equals(value.source()))audit(key,"REPORT_DISCREPANCY",operator,value.evidence(),before,value);
                return before;
            }
            boolean documentedGap=before.stage()==Stage.RELEASE && "unreported".equals(before.status()) && "REPORT".equals(value.source());
            if(!documentedGap && before.effectiveAt()!=null && (value.effectiveAt()==null || value.effectiveAt().isBefore(before.effectiveAt())))return before;
            if(before.effectiveAt()==null && before.observedAt().isAfter(value.observedAt()))return before;
            int changed=jdbc.update("update financial_entries set document=?,entry_date=?,currency=?,certainty=?,source_kind=?,observed_at=?,updated_at=?,version=version+1 where entry_key=? and version=?",
                    json(value),value.date(),value.currency(),value.certainty().name(),value.source(),Timestamp.from(value.observedAt()),Timestamp.from(Instant.now()),key,existing.getFirst().getValue());
            if(changed!=1)throw new IllegalStateException("Registro alterado por outra operação; atualize e tente novamente.");
        }else jdbc.update("insert into financial_entries(entry_key,identity_hash,provider,account_key,environment,stage,source_kind,entry_date,currency,certainty,document,observed_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                key,key,value.provider().name(),value.account(),value.environment().name(),value.stage().name(),value.source(),value.date(),value.currency(),value.certainty().name(),json(value),Timestamp.from(value.observedAt()),Timestamp.from(Instant.now()));
        audit(key,before==null?"CREATE":"UPDATE",operator,value.evidence(),before,value);
        return value;
    }
    private static int rank(String source){return switch(source){case "API"->4;case "WEBHOOK"->3;case "REPORT","MANUAL"->2;default->1;};}
    public void audit(String key,String action,String operator,String evidence,Object before,Object after){
        jdbc.update("insert into financial_audit(entry_key,action,operator_name,evidence,before_document,after_document,created_at) values(?,?,?,?,?,?,?)",
                key,action,operator,evidence,before==null?null:json(before),json(after),Timestamp.from(Instant.now()));
    }
    @Transactional
    public SettlementRule saveRule(SettlementRule input){input.validate();
        SettlementRule rule=new SettlementRule(UUID.randomUUID().toString(),input.provider(),input.account(),input.environment(),input.country(),input.currency(),input.method(),input.installment(),input.validFrom(),input.validUntil(),input.zone(),input.cutoff(),input.releaseDays(),input.businessDays(),input.rollForward(),input.holidays(),input.schedule(),input.payoutDay(),input.bankDays(),input.firstPayoutDays(),input.firstPayment(),input.minimum(),input.reserve(),input.estimatedFeePercent(),input.estimatedFixedFee(),input.blocked(),input.evidence(),input.operator(),input.dateBase(),input.payoutCutoff());
        jdbc.update("insert into financial_rules(rule_id,document,created_at) values(?,?,?)",rule.id(),json(rule),Timestamp.from(Instant.now()));
        audit(rule.id(),"RULE",rule.operator(),rule.evidence(),null,rule);return rule;
    }
    public Optional<SettlementRule> rule(FinancialEntry payment){var effective=FinancialAgenda.project(payment,entries());return rules().stream().filter(r->r.matches(effective))
            .max(Comparator.comparingInt(SettlementRule::specificity).thenComparing(SettlementRule::validFrom));}
    public List<FinancialEntry> preview(FinancialEntry payment,SettlementRule rule){
        return preview(payment,rule,null);
    }
    public List<FinancialEntry> preview(FinancialEntry payment,SettlementRule rule,BigDecimal eligibleBalance){
        payment=FinancialAgenda.project(payment,entries());
        rule.validate(); if(!rule.matches(payment))throw new IllegalArgumentException("Regra não cobre pagamento/vigência.");
        BigDecimal fee=null,net=null;
        if(payment.gross()!=null && rule.estimatedFeePercent()!=null && rule.estimatedFixedFee()!=null){
            fee=payment.gross().multiply(rule.estimatedFeePercent()).movePointLeft(2).add(rule.estimatedFixedFee()).setScale(6,java.math.RoundingMode.HALF_UP);
            // Taxes/withholding unknown: never invent a final net. Estimate only when tax is explicitly known.
            if(payment.tax()!=null)net=payment.gross().subtract(fee).subtract(payment.tax());
            if(net!=null && net.signum()<0)net=null;
        }
        FinancialEntry release=new FinancialEntry(null,payment.provider(),payment.account(),payment.environment(),payment.resourceId(),payment.installment(),Stage.RELEASE,payment.key(),payment.storeId(),payment.subscriptionId(),payment.country(),payment.method(),payment.currency(),payment.gross(),fee,payment.tax(),net,rule.releaseDate(payment),payment.paidDate(),payment.paidAt(),"pending",Certainty.ESTIMATED,"CALCULATION",rule.evidence(),Instant.now(),null,rule.id());
        FinancialEntry payout=new FinancialEntry(null,payment.provider(),payment.account(),payment.environment(),"forecast:"+payment.resourceId(),payment.installment(),Stage.PAYOUT,payment.key(),payment.storeId(),payment.subscriptionId(),payment.country(),payment.method(),payment.currency(),payment.gross(),null,payment.tax(),net,rule.payoutDate(payment,eligibleBalance),payment.paidDate(),payment.paidAt(),rule.blocked()?"blocked":rule.schedule()==SettlementRule.PayoutSchedule.MANUAL?"on_request":"forecast",Certainty.ESTIMATED,"CALCULATION",rule.evidence()+"; previsão individual, não é transferência criada; mínimo depende do saldo elegível da conta",Instant.now(),null,rule.id());
        return List.of(release,payout);
    }
    @Transactional
    public void estimate(String paymentKey,String ruleId,String operator,boolean recalculate){
        FinancialEntry payment=require(paymentKey); if(payment.stage()!=Stage.PAYMENT)throw new IllegalArgumentException("Selecione um pagamento.");
        SettlementRule rule=rules().stream().filter(r->r.id().equals(ruleId)).findFirst().orElseThrow();
        for(var estimate:preview(payment,rule)){
            String key=hash(estimate.identity());var current=jdbc.queryForList("select document from financial_entries where entry_key=?",String.class,key);
            if(!current.isEmpty()) {
                var fact=read(current.getFirst(),FinancialEntry.class);
                if(!"CALCULATION".equals(fact.source())) {
                    var projected=fact;
                    if((fact.date()==null || recalculate && fact.dateCertainty()==Certainty.ESTIMATED) && estimate.date()!=null)
                        projected=fact.withForecastDate(estimate.date(),rule.id());
                    if((fact.fee()==null || recalculate && fact.amountCertainty()==Certainty.ESTIMATED) && estimate.fee()!=null && Objects.equals(fact.currency(),estimate.currency()))
                        projected=projected.withEstimatedAmounts(estimate.fee(),fact.net()==null||fact.amountCertainty()==Certainty.ESTIMATED?estimate.net():fact.net());
                    if(!projected.equals(fact))record(projected,operator);
                    continue;
                }
                if(!recalculate || fact.dateCertainty()!=Certainty.ESTIMATED)continue;
            }
            record(estimate,operator);
        }
    }
    @Transactional
    public void allocate(String payoutKey,String releaseKey,BigDecimal amount,String evidence,String operator){
        // Serialize allocations within a payout and release to enforce both limits under concurrency.
        for(String key:java.util.stream.Stream.of(payoutKey,releaseKey).sorted().toList())
            jdbc.queryForList("select entry_key from financial_entries where entry_key=? for update",key);
        FinancialEntry payout=require(payoutKey),release=require(releaseKey);
        if(operator==null || operator.isBlank() || operator.length()>120 || evidence==null || evidence.isBlank() || evidence.length()>2000
                || amount==null || amount.precision()>19 || amount.scale()>6)throw new IllegalArgumentException("Responsável, evidência e valor válidos são obrigatórios.");
        if("CALCULATION".equals(payout.source()) || release.amountCertainty()==Certainty.ESTIMATED)throw new IllegalArgumentException("Relacione movimentos reais, não previsões.");
        if(payout.stage()!=Stage.PAYOUT || release.stage()!=Stage.RELEASE || !payout.account().equals(release.account())
                || payout.provider()!=release.provider() || payout.environment()!=release.environment() || !Objects.equals(payout.currency(),release.currency())
                || amount==null || amount.signum()<=0 || payout.net()==null || release.net()==null || evidence==null || evidence.isBlank())throw new IllegalArgumentException("Vínculo exige valores líquidos conhecidos na mesma conta/moeda e evidência.");
        var old=jdbc.queryForList("select amount from financial_links where payout_key=? and release_key=?",BigDecimal.class,payoutKey,releaseKey);
        if(!old.isEmpty()) {if(old.getFirst().compareTo(amount)==0)return;throw new IllegalArgumentException("Vínculo existente; registre uma correção auditável.");}
        BigDecimal paid=jdbc.queryForObject("select coalesce(sum(amount),0) from financial_links where payout_key=?",BigDecimal.class,payoutKey);
        BigDecimal released=jdbc.queryForObject("select coalesce(sum(amount),0) from financial_links where release_key=?",BigDecimal.class,releaseKey);
        if(paid.add(amount).compareTo(payout.net())>0 || released.add(amount).compareTo(release.net())>0)throw new IllegalArgumentException("Rateio excede o líquido conhecido.");
        jdbc.update("insert into financial_links(payout_key,release_key,amount,currency,evidence,operator_name,created_at) values(?,?,?,?,?,?,?)",payoutKey,releaseKey,amount,payout.currency(),evidence,operator,Timestamp.from(Instant.now()));
        audit(payoutKey,"ALLOCATION",operator,evidence,null,Map.of("release",releaseKey,"amount",amount));
    }
    public void syncStatus(String scope,PaymentProviderType provider,String account,PaymentEnvironment environment,String coverage,boolean success){
        var rows=jdbc.queryForList("select scope_key from financial_sync where scope_key=?",scope);
        if(rows.isEmpty())jdbc.update("insert into financial_sync(scope_key,provider,account_key,environment,attempted_at,coverage) values(?,?,?,?,?,?)",scope,provider.name(),account,environment.name(),Timestamp.from(Instant.now()),coverage);
        jdbc.update("update financial_sync set attempted_at=?,coverage=?,error_message=? where scope_key=?",Timestamp.from(Instant.now()),coverage,success?null:"Consulta pendente; dados anteriores preservados.",scope);
        if(success)jdbc.update("update financial_sync set succeeded_at=? where scope_key=?",Timestamp.from(Instant.now()),scope);
    }
}
