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
import java.time.Instant;
import java.util.*;

@Service
public class DlocalGoRefundService {
    private org.springframework.beans.factory.ObjectProvider<DlocalGoUpgradeService> upgrades;
    @org.springframework.beans.factory.annotation.Autowired
    public void setUpgrades(org.springframework.beans.factory.ObjectProvider<DlocalGoUpgradeService> value) {upgrades=value;}
    private final DlocalGoGateway gateway;
    private final DlocalGoFinancialService finances;
    private final DlocalGoOperationRepository operations;
    private final DlocalGoReversalRepository reversals;
    private final PaymentSubscriptionRepository subscriptions;
    private final StoreRepository stores;
    private final ObjectProvider<PaymentSubscriptionService> payments;
    private final NuvemshopProperties properties;
    private final TransactionTemplate tx;
    public DlocalGoRefundService(DlocalGoGateway gateway,DlocalGoFinancialService finances,DlocalGoOperationRepository operations,
            DlocalGoReversalRepository reversals,PaymentSubscriptionRepository subscriptions,StoreRepository stores,
            ObjectProvider<PaymentSubscriptionService> payments,NuvemshopProperties properties,PlatformTransactionManager manager) {
        this.gateway=gateway;this.finances=finances;this.operations=operations;this.reversals=reversals;this.subscriptions=subscriptions;
        this.stores=stores;this.payments=payments;this.properties=properties;
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public List<DlocalGoOperation> recent(){return operations.findTop100ByOrderByCreatedAtDesc();}
    public DlocalGoOperation require(String id) {
        var op=operations.findById(id).orElseThrow();
        if(op.getKind()!=Kind.REFUND || op.getEnvironment()!=gateway.environment()) throw new IllegalArgumentException("Reembolso não encontrado neste ambiente.");
        return op;
    }
    public DlocalGoOperation quote(Long storeId,String invoiceId,BigDecimal amount) {
        return tx.execute(status -> {
            stores.findByStoreIdForUpdate(storeId).orElseThrow();var sub=subscription(storeId);
            if(sub.isUpgradePaymentPending() && !invoiceId.startsWith("upgrade:")) throw new IllegalArgumentException("Conclua o upgrade pendente antes de reembolsar a mensalidade.");
            GatewayInvoice invoice;
            if(invoiceId.startsWith("upgrade:")) {
                var adjustment=operations.findById(invoiceId.substring(8)).orElseThrow();
                if(adjustment.getKind()!=Kind.UPGRADE || Set.of(State.QUOTED,State.FAILED,State.REVERSED).contains(adjustment.getState()) || !storeId.equals(adjustment.getStoreId())
                        || adjustment.getEnvironment()!=gateway.environment()) throw new IllegalArgumentException("Ajuste de upgrade inválido.");
                invoice=new GatewayInvoice(invoiceId,adjustment.getSubscriptionId(),adjustment.reference(),
                        finances.paymentStatus(adjustment.reference(),"ARS",adjustment.getDueAmount()),"ARS",adjustment.getDueAmount());
            } else invoice=gateway.getInvoice(invoiceId);
            if(!Objects.equals(sub.getProviderSubscriptionId(),invoice.subscriptionId()) || !"ARS".equals(invoice.currency())
                    || !invoice.approved()) throw new IllegalArgumentException("Cobrança não pertence à assinatura ou não está paga.");
            JsonNode payment=gateway.paymentForOrder(invoice.paymentId());
            if(!"ARS".equals(payment.path("currency").asText()) || invoice.amountPaid().compareTo(new BigDecimal(payment.path("amount").asText()))!=0)
                throw new PaymentGatewayException("Valor de pagamento divergente.");
            checkAmount(storeId,invoice.paymentId(),invoice.amountPaid(),amount,null);
            var op=new DlocalGoOperation();op.setKind(Kind.REFUND);op.setStoreId(storeId);op.setEnvironment(gateway.environment());
            op.setSubscriptionId(sub.getProviderSubscriptionId());op.setSourcePaymentId(invoice.paymentId());op.setInvoiceId(invoiceId);
            op.setPaymentId(payment.path("id").asText());op.setSourceAmount(invoice.amountPaid());op.setDueAmount(amount);
            op.setExpiresAt(Instant.now().plusSeconds(900));return operations.save(op);
        });
    }
    public void execute(String id) {
        boolean claimed=Boolean.TRUE.equals(tx.execute(status -> {
            var op=require(id);stores.findByStoreIdForUpdate(op.getStoreId()).orElseThrow();op=operations.lockById(id).orElseThrow();
            if(op.getState()!=State.QUOTED) return false;
            var sub=subscription(op.getStoreId());
            if((sub.isUpgradePaymentPending() && !op.getInvoiceId().startsWith("upgrade:")) || !Objects.equals(sub.getProviderSubscriptionId(),op.getSubscriptionId()) || !Instant.now().isBefore(op.getExpiresAt()))
                throw new IllegalArgumentException("Resumo expirado ou assinatura alterada.");
            finances.paymentStatus(op.getSourcePaymentId(),"ARS",op.getSourceAmount());
            checkAmount(op.getStoreId(),op.getSourcePaymentId(),op.getSourceAmount(),op.getDueAmount(),id);
            op.setState(State.CREATING);operations.saveAndFlush(op);return true;
        }));
        if(!claimed) return; // An indeterminate POST is never sent twice.
        var op=require(id);var sub=subscription(op.getStoreId());
        JsonNode response=gateway.refund(op.getPaymentId(),op.getDueAmount(),properties.appBaseUrl()+"/webhooks/dlocal-go?reference="+sub.getExternalReference());
        String remoteId=response.path("id").asText();
        if(!remoteId.matches("[A-Za-z0-9-]{1,120}")) throw new PaymentGatewayException("Reembolso sem identificador válido.");
        tx.executeWithoutResult(status -> {var current=operations.lockById(id).orElseThrow();current.setRemoteId(remoteId);current.setState(State.PENDING);operations.saveAndFlush(current);});
        reconcile(id);
    }
    public void reconcile(String id) {
        var op=require(id);
        if(op.getRemoteId()==null || Set.of(State.QUOTED,State.FAILED).contains(op.getState())) return;
        JsonNode remote=gateway.refundDetail(op.getRemoteId());
        if(!op.getRemoteId().equals(remote.path("id").asText()) || op.getDueAmount().compareTo(new BigDecimal(remote.path("amount").asText()))!=0
                || !op.getSourcePaymentId().equals(remote.path("order_id").asText()) || !"ARS".equals(remote.path("currency").asText()))
            throw new PaymentGatewayException("Resposta de reembolso divergente.");
        String state=remote.path("status").asText();
        if(!Set.of("PENDING","SUCCESS","REJECTED","CANCELLED").contains(state)) throw new PaymentGatewayException("Estado de reembolso desconhecido.");
        tx.executeWithoutResult(status -> {
            stores.findByStoreIdForUpdate(op.getStoreId()).orElseThrow();var current=operations.lockById(id).orElseThrow();
            if(current.getState()==State.COMPLETED) return;
            current.setState("SUCCESS".equals(state)?State.COMPLETED:("PENDING".equals(state)?State.PENDING:State.FAILED));operations.saveAndFlush(current);
            if("SUCCESS".equals(state)) {
                var entry=new DlocalGoReversal();entry.setId(gateway.environment()+":REFUND:"+op.getRemoteId());entry.setEnvironment(gateway.environment());
                entry.setOrderId(op.getSourcePaymentId());entry.setKind("REFUND");entry.setStatus("SUCCESS");entry.setCurrency("ARS");entry.setAmount(op.getDueAmount());
                reversals.saveAndFlush(entry);
            }
        });
        // Fetch the current invoice, so refunding an old cycle never revokes a newer paid cycle.
        if(upgrades!=null) upgrades.getObject().reconcileStore(op.getStoreId());
        payments.getObject().reconcile(op.getStoreId());
    }
    public void reconcileStore(Long storeId) {
        for(var op:operations.findByStoreIdOrderByCreatedAtDesc(storeId))
            if(op.getKind()==Kind.REFUND && op.getEnvironment()==gateway.environment() && op.getState()==State.PENDING) reconcile(op.getId());
    }
    @Scheduled(fixedDelayString="${payments.dlocal-go.refund-reconcile-delay-ms:60000}")
    public void retry() {
        if(!gateway.operational()) return;
        for(var op:operations.findByKindAndStateIn(Kind.REFUND,List.of(State.PENDING)))
            if(op.getEnvironment()==gateway.environment()) try{reconcile(op.getId());}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("payments.dlocal_go.refund_pending operation_id={} type={}",op.getId(),ex.getClass().getSimpleName());}
    }
    private PaymentSubscription subscription(Long storeId) {
        var sub=subscriptions.findByStoreId(storeId).orElseThrow();
        if(sub.getProvider()!=PaymentProviderType.DLOCAL_GO || sub.getProviderEnvironment()!=gateway.environment() || !"ARS".equals(sub.getCurrency()))
            throw new IllegalArgumentException("Esta operação exige uma assinatura dLocal Go em ARS no ambiente atual.");
        return sub;
    }
    private void checkAmount(Long storeId,String order,BigDecimal paid,BigDecimal amount,String except) {
        if(amount==null || amount.signum()<=0 || amount.scale()>2) throw new IllegalArgumentException("Valor de reembolso inválido.");
        BigDecimal reserved=operations.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .filter(o -> o.getKind()==Kind.REFUND && o.getEnvironment()==gateway.environment() && order.equals(o.getSourcePaymentId())
                        && !o.getId().equals(except) && Set.of(State.CREATING,State.PENDING,State.REVIEW).contains(o.getState()))
                .map(DlocalGoOperation::getDueAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(amount.add(reserved).add(finances.refunded(order)).compareTo(paid)>0)
            throw new IllegalArgumentException("Valor excede o saldo disponível para reembolso.");
    }
}
