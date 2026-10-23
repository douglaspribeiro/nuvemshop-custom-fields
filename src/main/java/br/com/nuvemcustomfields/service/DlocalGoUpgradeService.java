package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.entity.DlocalGoOperation.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.*;
import java.time.*;
import java.util.*;

/** dLocal-only proportional adjustment: does not use change-plan, which charges a full month. */
@Service
public class DlocalGoUpgradeService {
    private final DlocalGoOperationRepository operations;
    private final PaymentSubscriptionRepository subscriptions;
    private final StoreRepository stores;
    private final DlocalGoGateway gateway;
    private final DlocalGoFinancialService finances;
    private final ObjectProvider<PaymentSubscriptionService> payments;
    private final NuvemshopProperties properties;
    private final PaymentNotificationService notifications;
    private final TransactionTemplate tx;
    public DlocalGoUpgradeService(DlocalGoOperationRepository operations,PaymentSubscriptionRepository subscriptions,
            StoreRepository stores,DlocalGoGateway gateway,DlocalGoFinancialService finances,ObjectProvider<PaymentSubscriptionService> payments,
            NuvemshopProperties properties,PaymentNotificationService notifications,PlatformTransactionManager manager) {
        this.operations=operations;this.subscriptions=subscriptions;this.stores=stores;this.gateway=gateway;this.finances=finances;
        this.payments=payments;this.properties=properties;this.notifications=notifications;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public DlocalGoOperation quote(Long storeId,PlanType target) {
        return tx.execute(status -> {
            Store store=lock(storeId); var sub=eligible(store,target);
            if(operations.findByStoreIdOrderByCreatedAtDesc(storeId).stream().anyMatch(o -> o.getKind()==Kind.REFUND
                    && o.getEnvironment()==gateway.environment() && Set.of(State.CREATING,State.PENDING,State.REVIEW).contains(o.getState())))
                throw new IllegalArgumentException("Existe um reembolso aguardando confirmação.");
            if(sub.isUpgradePaymentPending() || sub.getUpgradePlan()!=null) throw new IllegalArgumentException("Existe um upgrade aguardando confirmação.");
            var remote=gateway.getSubscription(sub.getProviderSubscriptionId());
            var invoice=gateway.getLatestInvoice(remote.id()).orElseThrow();
            if(!Objects.equals(sub.getExternalReference(),remote.externalReference()) || !Objects.equals(sub.getProviderCheckoutId(),remote.checkoutResourceId())
                    || !"active".equals(remote.status()) || !"ARS".equals(remote.currency()) || sub.getAmountValue().compareTo(remote.amount())!=0
                    || !"paid".equals(invoice.paymentStatus()) || !remote.id().equals(invoice.subscriptionId())
                    || !"ARS".equals(invoice.currency()) || invoice.amountPaid().compareTo(sub.getAmountValue())!=0)
                throw new IllegalArgumentException("Concilie o pagamento da assinatura antes do upgrade.");
            Instant now=Instant.now(),start=remote.currentPeriodStart(),end=remote.currentPeriodEnd();
            if(start==null || end==null || !start.isBefore(now) || !now.isBefore(end)) throw new IllegalArgumentException("Período pago inválido.");
            if(operations.findByStoreIdOrderByCreatedAtDesc(storeId).stream().anyMatch(o -> o.getKind()==Kind.UPGRADE
                    && o.getEnvironment()==gateway.environment() && (Set.of(State.CREATING,State.PENDING,State.PAID,State.APPLYING,State.REVIEW).contains(o.getState())
                    || (Set.of(State.COMPLETED,State.REVERSED).contains(o.getState()) && end.equals(o.getPeriodEnd())))))
                throw new IllegalArgumentException("Já existe um upgrade para este ciclo.");
            BigDecimal targetAmount=gateway.amount(store,target);
            BigDecimal ratio=BigDecimal.valueOf(Duration.between(now,end).toMillis())
                    .divide(BigDecimal.valueOf(Duration.between(start,end).toMillis()),16,RoundingMode.HALF_UP);
            BigDecimal due=targetAmount.subtract(sub.getAmountValue()).multiply(ratio).setScale(2,RoundingMode.HALF_UP);
            if(due.signum()<=0) throw new IllegalArgumentException("Não há ajuste proporcional positivo neste ciclo.");
            var op=new DlocalGoOperation();op.setKind(Kind.UPGRADE);op.setStoreId(storeId);op.setEnvironment(gateway.environment());
            op.setSubscriptionId(sub.getProviderSubscriptionId());op.setSourcePlan(sub.getPlan());op.setTargetPlan(target);
            op.setSourceAmount(sub.getAmountValue());op.setTargetAmount(targetAmount);op.setDueAmount(due);
            op.setSourcePriceId(sub.getProviderPriceId());op.setTargetPriceId(gateway.priceId(store,target));
            op.setSourcePaymentId(invoice.paymentId());op.setPeriodStart(start);op.setPeriodEnd(end);
            op.setExpiresAt(now.plusSeconds(900).isBefore(end)?now.plusSeconds(900):end);
            return operations.save(op);
        });
    }
    public DlocalGoOperation owned(Long storeId,String id) {
        var op=operations.findById(id).orElseThrow();
        if(op.getKind()!=Kind.UPGRADE || !storeId.equals(op.getStoreId()) || op.getEnvironment()!=gateway.environment())
            throw new IllegalArgumentException("Upgrade não encontrado.");
        return op;
    }
    public String pay(Long storeId,String id) {
        boolean claimed=Boolean.TRUE.equals(tx.execute(status -> {
            var store=lock(storeId);var op=owned(storeId,id);op=operations.lockById(op.getId()).orElseThrow();
            if(op.getState()!=State.QUOTED) return false;
            var sub=eligible(store,op.getTargetPlan());
            if(sub.isUpgradePaymentPending() || sub.getUpgradePlan()!=null || !Instant.now().isBefore(op.getExpiresAt())
                    || !op.getSubscriptionId().equals(sub.getProviderSubscriptionId()) || sub.getPlan()!=op.getSourcePlan()
                    || sub.getAmountValue().compareTo(op.getSourceAmount())!=0 || gateway.amount(store,op.getTargetPlan()).compareTo(op.getTargetAmount())!=0
                    || !Objects.equals(gateway.priceId(store,op.getTargetPlan()),op.getTargetPriceId()))
                throw new IllegalArgumentException("O resumo expirou ou a assinatura mudou. Revise o upgrade.");
            var remote=gateway.getSubscription(op.getSubscriptionId());
            if(!op.getPeriodEnd().equals(remote.currentPeriodEnd()) || !"active".equals(remote.status()))
                throw new IllegalArgumentException("O ciclo mudou. Revise o upgrade.");
            op.setState(State.CREATING);operations.saveAndFlush(op);
            sub.setUpgradePaymentPending(true);subscriptions.saveAndFlush(sub);return true;
        }));
        var op=owned(storeId,id);
        if(claimed) {
            var sub=subscriptions.findByStoreId(storeId).orElseThrow();
            JsonNode remote=gateway.createAdjustment(op.reference(),op.getDueAmount(),
                    properties.appBaseUrl()+"/admin/billing/upgrade/dlocal-go/processing?id="+id,sub.getExternalReference());
            validatePayment(op,remote);
            tx.executeWithoutResult(status -> { lock(storeId);var current=operations.lockById(id).orElseThrow();
                current.setPaymentId(remote.path("id").asText());current.setCheckoutUrl(remote.path("redirect_url").asText());
                if(current.getState()==State.CREATING) current.setState(State.PENDING);operations.saveAndFlush(current); });
        }
        op=owned(storeId,id);
        return op.getCheckoutUrl();
    }
    public void reconcile(String id) {
        var op=operations.findById(id).orElseThrow();
        if(op.getKind()!=Kind.UPGRADE || op.getEnvironment()!=gateway.environment() || Set.of(State.QUOTED,State.FAILED,State.REVIEW).contains(op.getState())) return;
        if(Set.of(State.COMPLETED,State.REVERSED).contains(op.getState()) && !Instant.now().isBefore(op.getPeriodEnd())) return;
        JsonNode payment=op.getPaymentId()==null?gateway.paymentForOrder(op.reference()):gateway.payment(op.getPaymentId());
        validatePayment(op,payment);
        String raw=payment.path("status").asText();
        String state=Set.of("PAID","COMPLETED").contains(raw)?finances.paymentStatus(op.reference(),"ARS",op.getDueAmount()):raw;
        if(Set.of("refunded","chargedBack").contains(state)) { reverse(op);return; }
        if(!Set.of("paid","partialRefund").contains(state)) {
            if(Set.of("REJECTED","CANCELLED","EXPIRED").contains(raw) && op.getState()!=State.COMPLETED)
                tx.executeWithoutResult(status -> {lock(op.getStoreId());var current=operations.lockById(id).orElseThrow();current.setState(State.FAILED);operations.save(current);
                    var sub=subscriptions.findByStoreId(op.getStoreId()).orElseThrow();sub.setUpgradePaymentPending(false);subscriptions.save(sub);});
            return;
        }
        if(op.getState()==State.COMPLETED) return;
        if(operations.findByStoreIdOrderByCreatedAtDesc(op.getStoreId()).stream().anyMatch(refund -> refund.getKind()==Kind.REFUND
                && refund.getEnvironment()==gateway.environment() && op.reference().equals(refund.getSourcePaymentId())
                && Set.of(State.CREATING,State.PENDING,State.REVIEW).contains(refund.getState()))) return;
        tx.executeWithoutResult(status -> {lock(op.getStoreId());var current=operations.lockById(id).orElseThrow();
            current.setPaymentId(payment.path("id").asText());current.setState(State.APPLYING);operations.saveAndFlush(current);});
        var sub=subscriptions.findByStoreId(op.getStoreId()).orElseThrow();
        if(!op.getSubscriptionId().equals(sub.getProviderSubscriptionId()) || !Instant.now().isBefore(op.getPeriodEnd())
                || sub.isCancellationPending() || sub.getCancellationEffectiveAt()!=null)
            throw new PaymentGatewayException("Ajuste pago aguardando conciliação da assinatura; não pague novamente.");
        var remote=gateway.getSubscription(op.getSubscriptionId());
        if(!op.getPeriodEnd().equals(remote.currentPeriodEnd()) || !sub.getExternalReference().equals(remote.externalReference()))
            throw new PaymentGatewayException("O ciclo mudou após o pagamento do ajuste.");
        gateway.updateExclusivePlan(sub.getProviderCheckoutId(),sub.getExternalReference(),op.getSourceAmount(),op.getTargetAmount());
        tx.executeWithoutResult(status -> {
            Store store=lock(op.getStoreId());var current=operations.lockById(id).orElseThrow();
            var local=subscriptions.findByStoreId(op.getStoreId()).orElseThrow();
            if(current.getState()==State.COMPLETED) return;
            if(local.getProvider()!=PaymentProviderType.DLOCAL_GO || !op.getSubscriptionId().equals(local.getProviderSubscriptionId()))
                throw new PaymentGatewayException("Contrato divergente do upgrade.");
            local.setPlan(op.getTargetPlan());local.setAmountValue(op.getTargetAmount());local.setProviderPriceId(op.getTargetPriceId());
            local.setUpgradePaymentPending(false);subscriptions.saveAndFlush(local);
            payments.getObject().reconcile(op.getStoreId());
            notifications.enqueueUpgrade(store,local,op.getSourcePlan(),new GatewayInvoice("dlocal-upgrade-"+id,op.getSubscriptionId(),op.reference(),"paid","ARS",op.getDueAmount()));
            current.setState(State.COMPLETED);operations.saveAndFlush(current);
        });
    }
    private void reverse(DlocalGoOperation op) {
        if(op.getState()==State.REVERSED) return;
        var sub=subscriptions.findByStoreId(op.getStoreId()).orElseThrow();
        if(op.getSubscriptionId().equals(sub.getProviderSubscriptionId()) && Set.of(State.APPLYING,State.COMPLETED).contains(op.getState())
                && Instant.now().isBefore(op.getPeriodEnd()) && op.getSourcePaymentId().equals(sub.getLastPaymentId())) {
            gateway.updateExclusivePlan(sub.getProviderCheckoutId(),sub.getExternalReference(),op.getTargetAmount(),op.getSourceAmount());
            tx.executeWithoutResult(status -> {lock(op.getStoreId());var local=subscriptions.findByStoreId(op.getStoreId()).orElseThrow();
                local.setPlan(op.getSourcePlan());local.setAmountValue(op.getSourceAmount());local.setProviderPriceId(op.getSourcePriceId());
                local.setUpgradePaymentPending(false);subscriptions.saveAndFlush(local);
                var current=operations.lockById(op.getId()).orElseThrow();current.setState(State.REVERSED);operations.saveAndFlush(current);
                payments.getObject().reconcile(op.getStoreId());});
        } else tx.executeWithoutResult(status -> {lock(op.getStoreId());var current=operations.lockById(op.getId()).orElseThrow();
            current.setState(State.REVERSED);operations.save(current);
            var local=subscriptions.findByStoreId(op.getStoreId()).orElseThrow();local.setUpgradePaymentPending(false);subscriptions.save(local);});
    }
    public boolean coversInvoice(PaymentSubscription local,GatewayInvoice invoice) {
        return operations.findByStoreIdOrderByCreatedAtDesc(local.getStoreId()).stream().anyMatch(op -> op.getKind()==Kind.UPGRADE
                && op.getEnvironment()==gateway.environment() && Set.of(State.APPLYING,State.COMPLETED).contains(op.getState())
                && op.getSubscriptionId().equals(local.getProviderSubscriptionId()) && op.getTargetPlan()==local.getPlan()
                && op.getTargetAmount().compareTo(local.getAmountValue())==0 && op.getSourcePaymentId().equals(invoice.paymentId())
                && op.getSourceAmount().compareTo(invoice.amountPaid())==0 && Instant.now().isBefore(op.getPeriodEnd())
                && Set.of("paid","partialRefund").contains(finances.paymentStatus(op.reference(),"ARS",op.getDueAmount())));
    }
    public void reconcileStore(Long storeId) {
        for(var op:operations.findByStoreIdOrderByCreatedAtDesc(storeId))
            if(op.getKind()==Kind.UPGRADE && op.getEnvironment()==gateway.environment()) reconcile(op.getId());
    }
    @Scheduled(fixedDelayString="${payments.dlocal-go.upgrade-reconcile-delay-ms:60000}")
    public void retry() {
        if(!gateway.operational()) return;
        for(var op:operations.findByKindAndStateIn(Kind.UPGRADE,List.of(State.CREATING,State.PENDING,State.PAID,State.APPLYING,State.COMPLETED,State.REVERSED)))
            try{reconcile(op.getId());}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("payments.dlocal_go.upgrade_pending operation_id={} type={}",op.getId(),ex.getClass().getSimpleName());}
    }
    private void validatePayment(DlocalGoOperation op,JsonNode p) {
        if(!op.reference().equals(p.path("order_id").asText()) || !"AR".equals(p.path("country").asText())
                || !"ARS".equals(p.path("currency").asText()) || op.getDueAmount().compareTo(new BigDecimal(p.path("amount").asText()))!=0
                || (op.getPaymentId()!=null && !op.getPaymentId().equals(p.path("id").asText())))
            throw new PaymentGatewayException("Pagamento divergente do ajuste proporcional.");
    }
    private Store lock(Long id) { return stores.findActiveByStoreIdForUpdate(id).orElseThrow(); }
    private PaymentSubscription eligible(Store store,PlanType target) {
        var sub=subscriptions.findByStoreId(store.getStoreId()).orElseThrow();
        if(sub.getProvider()!=PaymentProviderType.DLOCAL_GO || sub.getProviderEnvironment()!=gateway.environment() || !"ARS".equals(sub.getCurrency())
                || !"AR".equalsIgnoreCase(store.getStoreCountryCode()) || !sub.isAccessActive() || sub.getStatus()!=PaymentSubscriptionStatus.ACTIVE
                || sub.isCancellationPending() || sub.getCancellationEffectiveAt()!=null || store.isCourtesyPremium() || target==null
                || !target.isUpgradeFrom(sub.getPlan()) || !gateway.planAvailable(store,target))
            throw new IllegalArgumentException("Assinatura indisponível para upgrade proporcional.");
        return sub;
    }
}
