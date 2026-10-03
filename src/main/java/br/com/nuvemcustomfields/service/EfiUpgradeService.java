package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.entity.UpgradeAdjustment.State;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class EfiUpgradeService {
    private final UpgradeAdjustmentRepository adjustments;
    private final PaymentSubscriptionRepository subscriptions;
    private final StoreRepository stores;
    private final PaymentSubscriptionService payments;
    private final EfiGateway efi;
    private final NuvemshopProperties properties;
    private final TransactionTemplate tx;
    private final boolean couponEnabled;
    private static final ZoneId BR = ZoneId.of("America/Sao_Paulo");

    public EfiUpgradeService(UpgradeAdjustmentRepository adjustments, PaymentSubscriptionRepository subscriptions,
            StoreRepository stores, PaymentSubscriptionService payments, EfiGateway efi, NuvemshopProperties properties,
            PlatformTransactionManager manager, @Value("${payments.upgrade.brinde-enabled:true}") boolean couponEnabled) {
        this.adjustments=adjustments; this.subscriptions=subscriptions; this.stores=stores;
        this.payments=payments; this.efi=efi; this.properties=properties;
        this.tx=new TransactionTemplate(manager); this.couponEnabled=couponEnabled;
    }

    public UpgradeAdjustment quote(Long storeId, PlanType target, String coupon) {
        return tx.execute(status -> {
            Store store=lockStore(storeId);
            PaymentSubscription sub=requireEligible(store, target);
            if (sub.isUpgradePaymentPending() || sub.getUpgradePlan()!=null)
                throw new IllegalArgumentException("Existe um upgrade em processamento. Consulte o andamento antes de tentar novamente.");
            JsonNode remote=efi.subscriptionDetail(sub.getProviderSubscriptionId());
            verifyCurrent(sub,remote);
            if(remote.path("plan").path("interval").asInt()!=1)
                throw new IllegalArgumentException("O ajuste proporcional está disponível apenas para assinaturas mensais.");
            Instant end=cycleEnd(remote);
            if(sub.getLastPaymentId()==null) throw new IllegalArgumentException("Precisamos confirmar o pagamento do ciclo atual antes do upgrade.");
            JsonNode paid=efi.chargeDetail(sub.getLastPaymentId());
            if(!sub.getLastPaymentId().equals(paid.path("charge_id").asText()) || !"paid".equals(paid.path("status").asText())
                    || !Objects.equals(sub.getExternalReference(), paid.path("custom_id").asText(null)))
                throw new IllegalArgumentException("O pagamento do ciclo atual ainda não foi confirmado.");
            Instant start=parseDate(paid.path("created_at").asText()).atStartOfDay(BR).toInstant();
            Instant now=Instant.now();
            // A cobrança confirmada deve pertencer ao ciclo atual, não a uma mensalidade antiga.
            if(!now.isBefore(end) || start.isAfter(now) || !start.isBefore(end)
                    || Duration.between(start,end).toDays()<27 || Duration.between(start,end).toDays()>32)
                throw new IllegalArgumentException("Não foi possível confirmar as datas do ciclo pago. Atualize a assinatura antes do upgrade.");
            if(adjustments.findByStoreIdOrderByQuotedAtDesc(storeId).stream()
                    .anyMatch(a -> a.isPending() || (a.getState()==State.COMPLETED && a.getPeriodEnd().equals(end))))
                throw new IllegalArgumentException("Já existe um upgrade para este ciclo. Aguarde a próxima renovação para outra alteração.");
            String code=coupon==null?"":coupon.trim().toUpperCase(Locale.ROOT);
            if(!code.isEmpty() && (!couponEnabled || !"BRINDE".equals(code) || target!=PlanType.PREMIUM_ULTRA))
                throw new IllegalArgumentException("Cupom inválido ou indisponível para este upgrade.");
            BigDecimal regular=efi.amount(target);
            BigDecimal paidAmount=BigDecimal.valueOf(paid.path("total").asLong(),2);
            if(paidAmount.signum()<=0 || paidAmount.compareTo(sub.getAmountValue())!=0)
                throw new IllegalArgumentException("O valor pago neste ciclo precisa ser conciliado antes do upgrade.");
            BigDecimal ratio=BigDecimal.valueOf(Duration.between(now,end).toMillis())
                    .divide(BigDecimal.valueOf(Duration.between(start,end).toMillis()),16,RoundingMode.HALF_UP);
            var values=calculate(paidAmount,regular,ratio,!code.isEmpty());
            UpgradeAdjustment a=new UpgradeAdjustment();
            a.setStoreId(storeId); a.setSubscriptionId(sub.getProviderSubscriptionId()); a.setEnvironment(efi.environment());
            a.setSourcePlan(sub.getPlan()); a.setTargetPlan(target); a.setSourceAmount(paidAmount); a.setRegularAmount(regular);
            a.setTargetPriceId(efi.planId(target)); a.setSourcePriceId(remote.path("plan").path("plan_id").asText());
            a.setTargetProrated(values.target()); a.setDiscountAmount(values.discount()); a.setCreditAmount(values.credit()); a.setDueAmount(values.due());
            a.setCouponCode(code.isEmpty()?null:code); a.setPeriodStart(start); a.setPeriodEnd(end); a.setQuotedAt(now);
            a.setExpiresAt(now.plusSeconds(900).isBefore(end)?now.plusSeconds(900):end);
            return adjustments.save(a);
        });
    }

    public record Calculation(BigDecimal target,BigDecimal discount,BigDecimal credit,BigDecimal due) {}
    public static Calculation calculate(BigDecimal source,BigDecimal target,BigDecimal ratio,boolean coupon) {
        if(ratio.signum()<0 || ratio.compareTo(BigDecimal.ONE)>0 || source.signum()<0 || target.signum()<=0)
            throw new IllegalArgumentException("Período ou preço inválido.");
        BigDecimal prorated=money(target.multiply(ratio));
        BigDecimal discounted=money(money(target.multiply(coupon?new BigDecimal("0.70"):BigDecimal.ONE)).multiply(ratio));
        BigDecimal credit=money(source.multiply(ratio));
        return new Calculation(prorated,prorated.subtract(discounted),credit,discounted.subtract(credit).max(BigDecimal.ZERO));
    }
    private static BigDecimal money(BigDecimal value){return value.setScale(2,RoundingMode.HALF_UP);}

    public UpgradeAdjustment owned(Long storeId,String id) {
        var a=adjustments.findById(id).orElseThrow(()->new IllegalArgumentException("Upgrade não encontrado."));
        if(!storeId.equals(a.getStoreId())) throw new IllegalArgumentException("Upgrade não encontrado.");
        return a;
    }
    public List<UpgradeAdjustment> history(Long storeId) {
        return adjustments.findByStoreIdOrderByQuotedAtDesc(storeId).stream().filter(a->a.getState()!=State.QUOTED).toList();
    }
    public List<UpgradeAdjustment> recent(){return adjustments.findTop100ByStateNotOrderByQuotedAtDesc(State.QUOTED);}

    public void pay(Long storeId,String id,EfiGateway.EfiPayer payer,String token) {
        boolean claimed=Boolean.TRUE.equals(tx.execute(status->{
            Store store=lockStore(storeId);
            UpgradeAdjustment a=locked(id,storeId);
            if(a.getState()!=State.QUOTED) return false; // Reenvio do formulário nunca cria outra cobrança.
            PaymentSubscription sub=requireEligible(store,a.getTargetPlan());
            if(sub.isUpgradePaymentPending() || sub.getUpgradePlan()!=null) throw new IllegalArgumentException("Outro upgrade está em processamento.");
            if(!Instant.now().isBefore(a.getExpiresAt()) || !sub.getProviderSubscriptionId().equals(a.getSubscriptionId())
                    || sub.getPlan()!=a.getSourcePlan() || sub.getAmountValue().compareTo(a.getSourceAmount())!=0
                    || efi.amount(a.getTargetPlan()).compareTo(a.getRegularAmount())!=0
                    || !Objects.equals(efi.planId(a.getTargetPlan()),a.getTargetPriceId()))
                throw new IllegalArgumentException("O resumo expirou ou o preço mudou. Revise o upgrade novamente.");
            JsonNode remote=efi.subscriptionDetail(a.getSubscriptionId()); verifyCurrent(sub,remote);
            if(!cycleEnd(remote).equals(a.getPeriodEnd())) throw new IllegalArgumentException("Seu ciclo mudou. Revise o upgrade novamente.");
            if(a.getDueAmount().signum()>0 && (!validPayer(payer) || token==null || !token.matches("[a-zA-Z0-9]{20,120}")))
                throw new IllegalArgumentException("Informe os dados de pagamento.");
            a.setState(a.getDueAmount().signum()==0?State.PAID:State.CREATING);
            if(a.getDueAmount().signum()==0) a.setPaidAt(Instant.now());
            sub.setUpgradePaymentPending(true); sub.setNextPaymentAt(a.getPeriodEnd()); subscriptions.save(sub); adjustments.save(a);
            return true;
        }));
        if(!claimed){reconcile(id);return;}
        UpgradeAdjustment a=owned(storeId,id);
        if(a.getState()==State.PAID){finish(id);return;}
        // A intenção foi COMMITADA antes da operação externa. Timeout nunca dispara nova criação.
        String chargeId=efi.createUpgradeCharge(a.getDueAmount(),a.reference(),
                properties.appBaseUrl()+"/prod/webhooks/efi-upgrades", "Ajuste proporcional - upgrade "+a.getSourcePlan().getDisplayName()+" para "+a.getTargetPlan().getDisplayName());
        tx.executeWithoutResult(status->{
            lockStore(storeId); var current=locked(id,storeId);
            if(current.getChargeId()!=null && !chargeId.equals(current.getChargeId())) throw new PaymentGatewayException("A cobrança precisa ser conciliada.");
            current.setChargeId(chargeId); current.setState(State.PAYMENT_PENDING); adjustments.save(current);
        });
        efi.payUpgradeCharge(chargeId,payer,token);
        reconcile(id);
    }

    public void reconcile(String id) {
        UpgradeAdjustment a=adjustments.findById(id).orElseThrow();
        if(a.getState()==State.COMPLETED || a.getState()==State.FAILED || a.getState()==State.QUOTED || a.getState()==State.REVIEW) return;
        if(a.getEnvironment()!=efi.environment()) throw new PaymentGatewayException("Ambiente do upgrade diferente do ambiente da Efí.");
        if(a.getChargeId()==null && a.getState()==State.CREATING){
            JsonNode found=efi.findUpgradeCharges(a.reference());
            if(!found.isArray())throw new PaymentGatewayException("Consulta de ajuste inválida.");
            if(found.size()>1){markReview(id,"Existe mais de uma cobrança para este ajuste. Contate o suporte; não pague novamente.");return;}
            if(found.size()==1){
                String chargeId=found.get(0).path("id").asText("");
                if(!chargeId.matches("\\d+"))throw new PaymentGatewayException("Cobrança sem identificador.");
                validateCharge(a,efi.chargeDetail(chargeId));
                final UpgradeAdjustment snapshot=a;
                tx.executeWithoutResult(status->{lockStore(snapshot.getStoreId());var current=locked(id,snapshot.getStoreId());
                    if(current.getChargeId()==null){current.setChargeId(chargeId);current.setState(State.PAYMENT_PENDING);adjustments.save(current);}});
                a=adjustments.findById(id).orElseThrow();
            }
            else if(Instant.now().isAfter(a.getExpiresAt().plusSeconds(6300))){
                markReview(id,"Não foi possível localizar a cobrança criada. Contate o suporte antes de fazer outro pagamento.");return;
            }
        }
        if(a.getChargeId()!=null){
            final UpgradeAdjustment snapshot=a;
            JsonNode charge=efi.chargeDetail(a.getChargeId());
            validateCharge(a,charge);
            if("new".equals(charge.path("status").asText()) && Instant.now().isAfter(a.getExpiresAt().plusSeconds(900))){
                // Somente a cobrança avulsa não paga expira. A assinatura nunca é cancelada.
                efi.cancelCharge(a.getChargeId());
                charge=efi.chargeDetail(a.getChargeId());validateCharge(a,charge);
            }
            final JsonNode confirmedCharge=charge;
            tx.executeWithoutResult(status->{
                lockStore(snapshot.getStoreId()); var current=locked(id,snapshot.getStoreId());
                if(current.getState()==State.COMPLETED || current.getState()==State.REVIEW) return;
                String paymentStatus=confirmedCharge.path("status").asText();
                if("paid".equals(paymentStatus)){
                    if(current.getPaidAt()==null) current.setPaidAt(Instant.now());
                    if(current.getState()!=State.CHANGE_PENDING) current.setState(State.PAID);
                }else if(Set.of("unpaid","canceled","refunded","contested").contains(paymentStatus)){
                    if(current.getPaidAt()!=null){current.setState(State.REVIEW);current.setMessage("O pagamento mudou de status. Entre em contato com o suporte.");}
                    else{current.setState(State.FAILED);current.setMessage("O ajuste não foi pago. Você continua no plano anterior.");
                        subscriptions.findByStoreId(current.getStoreId()).ifPresent(s->{s.setUpgradePaymentPending(false);subscriptions.save(s);});}
                }
                adjustments.save(current);
            });
        }
        a=adjustments.findById(id).orElseThrow();
        if(a.getState()==State.PAID || a.getState()==State.CHANGE_PENDING) finish(id);
    }

    private void finish(String id) {
        final UpgradeAdjustment initial=adjustments.findById(id).orElseThrow();
        tx.executeWithoutResult(status->{
            UpgradeAdjustment a=initial;
            Store store=lockStore(a.getStoreId()); var current=locked(id,a.getStoreId());
            if(current.getState()==State.COMPLETED || current.getState()==State.REVIEW) return;
            PaymentSubscription sub=subscriptions.findByStoreId(a.getStoreId()).orElseThrow();
            if(!a.getSubscriptionId().equals(sub.getProviderSubscriptionId()) || !sub.isAccessActive()
                    || sub.isCancellationPending() || sub.getCancellationEffectiveAt()!=null || store.isCourtesyPremium()){
                current.setState(State.REVIEW);current.setMessage("O ajuste foi pago, mas a assinatura mudou. Contate o suporte; não pague novamente.");
            }else{
                current.setState(State.CHANGE_PENDING);
                if(sub.getPlan()!=a.getTargetPlan()) sub.requestUpgrade(a.getTargetPlan(),a.getRegularAmount(),a.getTargetPriceId());
                subscriptions.save(sub);
            }
            adjustments.save(current);
        });
        UpgradeAdjustment a=adjustments.findById(id).orElseThrow();
        if(a.getState()!=State.CHANGE_PENDING) return;
        GatewaySubscription remote=efi.getSubscription(a.getSubscriptionId());
        if(!matchesTarget(a,remote)){
            if(!a.getSubscriptionId().equals(remote.id()) || !Objects.equals(remote.checkoutResourceId(),a.getSourcePriceId())
                    || remote.amount()==null || remote.amount().compareTo(a.getSourceAmount())!=0
                    || !Set.of("active","new_charge").contains(remote.status()) || !a.getPeriodEnd().equals(remote.nextPaymentAt())
                    || !Instant.now().isBefore(a.getPeriodEnd())) {
                markReview(id,"O ajuste foi pago, mas o ciclo ou o plano mudou. Contate o suporte; não pague novamente.");return;
            }
            efi.changeSubscriptionPlan(a.getSubscriptionId(),a.getTargetPriceId(),a.getTargetPlan().getDisplayName(),a.getRegularAmount());
        }
        final UpgradeAdjustment snapshot=a;
        tx.executeWithoutResult(status->{
            lockStore(snapshot.getStoreId()); var current=locked(id,snapshot.getStoreId());
            if(current.getState()==State.COMPLETED || current.getState()==State.REVIEW) return;
            PaymentSubscription sub=payments.reconcile(snapshot.getStoreId());
            if(sub.getPlan()!=snapshot.getTargetPlan() || sub.getAmountValue().compareTo(snapshot.getRegularAmount())!=0
                    || !snapshot.getSubscriptionId().equals(sub.getProviderSubscriptionId()))
                throw new PaymentGatewayException("A alteração do plano está aguardando confirmação.");
            sub.setUpgradePaymentPending(false);subscriptions.save(sub);
            current.setState(State.COMPLETED);current.setCompletedAt(Instant.now());current.setMessage(null);adjustments.save(current);
        });
    }

    public void receiveNotification(String token) {
        if(token==null || !token.matches("[a-zA-Z0-9-]{20,120}")) throw new IllegalArgumentException("Notificação inválida.");
        JsonNode history=efi.notification(token);
        if(!history.isArray() || history.isEmpty()) throw new IllegalArgumentException("Notificação vazia.");
        JsonNode last=history.get(history.size()-1);
        String reference=last.path("custom_id").asText(""); String chargeId=last.path("identifiers").path("charge_id").asText("");
        if(!reference.startsWith("upgrade-") || !chargeId.matches("\\d+")) return;
        String id=reference.substring(8);
        UpgradeAdjustment a=adjustments.findById(id).orElse(null);
        if(a==null || a.getState()==State.QUOTED || a.getEnvironment()!=efi.environment()) return;
        JsonNode charge=efi.chargeDetail(chargeId);
        validateCharge(a,charge);
        if(a.getState()==State.FAILED && "paid".equals(charge.path("status").asText())){
            markReview(id,"A Efí confirmou um pagamento após encerrar esta tentativa. Contate o suporte; não pague novamente.");return;
        }
        if(a.getState()==State.COMPLETED && Set.of("refunded","contested").contains(charge.path("status").asText())){
            markReview(id,"O pagamento do ajuste foi estornado ou contestado. Requer conferência com o suporte.");return;
        }
        tx.executeWithoutResult(status->{lockStore(a.getStoreId());var current=locked(id,a.getStoreId());
            if(current.getChargeId()==null){current.setChargeId(chargeId);adjustments.save(current);}});
        reconcile(id);
    }

    @Scheduled(fixedDelayString="${payments.upgrade.reconcile-delay-ms:60000}")
    public void retryPending(){
        for(var a:adjustments.findTop50ByStateInOrderByQuotedAtAsc(List.of(State.CREATING,State.PAYMENT_PENDING,State.PAID,State.CHANGE_PENDING))) {
            try{reconcile(a.getId());}catch(RuntimeException ex){LoggerFactory.getLogger(getClass()).warn("payments.upgrade.retry_pending id={} type={}",a.getId(),ex.getClass().getSimpleName());}
        }
    }
    private void markReview(String id,String message){tx.executeWithoutResult(status->{var a=adjustments.findById(id).orElseThrow();lockStore(a.getStoreId());a=locked(id,a.getStoreId());a.setState(State.REVIEW);a.setMessage(message);adjustments.save(a);subscriptions.findByStoreId(a.getStoreId()).ifPresent(s->{s.setUpgradePaymentPending(true);subscriptions.save(s);});});}
    private static boolean matchesTarget(UpgradeAdjustment a,GatewaySubscription remote){return a.getSubscriptionId().equals(remote.id()) && a.getTargetPriceId().equals(remote.checkoutResourceId()) && "BRL".equals(remote.currency()) && remote.amount()!=null && a.getRegularAmount().compareTo(remote.amount())==0 && a.getPeriodEnd().equals(remote.nextPaymentAt()) && Set.of("active","new_charge").contains(remote.status());}
    private static void validateCharge(UpgradeAdjustment a,JsonNode c){
        if(!a.reference().equals(c.path("custom_id").asText()) || (a.getChargeId()!=null && !a.getChargeId().equals(c.path("charge_id").asText()))
                || !c.has("total") || a.getDueAmount().movePointRight(2).longValueExact()!=c.path("total").asLong())
            throw new PaymentGatewayException("A cobrança não corresponde ao ajuste confirmado.");
    }
    private Store lockStore(Long id){return stores.findActiveByStoreIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("Loja ativa não encontrada."));}
    private UpgradeAdjustment locked(String id,Long store){var a=adjustments.lockById(id).orElseThrow();if(!store.equals(a.getStoreId()))throw new IllegalArgumentException("Upgrade não encontrado.");return a;}
    private PaymentSubscription requireEligible(Store store,PlanType target){
        PaymentSubscription sub=subscriptions.findByStoreId(store.getStoreId()).orElseThrow();
        if(target==null || !target.isUpgradeFrom(sub.getPlan()) || sub.getProvider()!=PaymentProviderType.EFI
                || sub.getProviderEnvironment()!=efi.environment() || !sub.isAccessActive() || sub.getStatus()!=PaymentSubscriptionStatus.ACTIVE
                || sub.isCancellationPending() || sub.getCancellationEffectiveAt()!=null || sub.isWinbackRestorePending()
                || store.isCourtesyPremium() || !efi.planAvailable(store,target)) throw new IllegalArgumentException("Esta assinatura não está disponível para upgrade proporcional.");
        return sub;
    }
    private static void verifyCurrent(PaymentSubscription sub,JsonNode remote){
        if(!sub.getProviderSubscriptionId().equals(remote.path("subscription_id").asText())
                || !Set.of("active","new_charge").contains(remote.path("status").asText())
                || !"credit_card".equals(remote.path("payment_method").asText())
                || (sub.getProviderPriceId()!=null && !sub.getProviderPriceId().equals(remote.path("plan").path("plan_id").asText()))
                || !Objects.equals(sub.getExternalReference(),remote.path("custom_id").asText(null))
                || sub.getAmountValue().movePointRight(2).longValueExact()!=remote.path("value").asLong())
            throw new IllegalArgumentException("A assinatura precisa estar conciliada e ativa no cartão antes do upgrade.");
    }
    private static Instant cycleEnd(JsonNode remote){return parseDate(remote.path("next_execution").asText()).atStartOfDay(BR).toInstant();}
    private static LocalDate parseDate(String value){try{return LocalDate.parse(value.substring(0,10));}catch(RuntimeException ex){throw new IllegalArgumentException("Não foi possível confirmar as datas do ciclo.");}}
    private static boolean validPayer(EfiGateway.EfiPayer p){return p!=null && p.name()!=null && !p.name().isBlank() && p.name().length()<=120
        && p.cpf()!=null && p.cpf().matches("\\d{11}") && p.email()!=null && p.email().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
        && p.phone()!=null && p.phone().matches("\\d{10,11}") && p.birth()!=null && p.birth().matches("\\d{4}-\\d{2}-\\d{2}");}
}
