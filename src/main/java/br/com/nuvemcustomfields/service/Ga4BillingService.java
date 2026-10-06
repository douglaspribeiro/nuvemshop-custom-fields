package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.GatewayInvoice;
import br.com.nuvemcustomfields.repository.Ga4OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class Ga4BillingService {
    private final Ga4OutboxRepository outbox;
    private final Ga4Client client;
    private final ObjectMapper mapper;
    public Ga4BillingService(Ga4OutboxRepository outbox,Ga4Client client,ObjectMapper mapper){
        this.outbox=outbox;this.client=client;this.mapper=mapper;
    }

    @Transactional
    public void payment(PaymentSubscription sub, GatewayInvoice invoice){
        if(invoice==null || !invoice.approved() || invoice.paymentId()==null || invoice.paymentId().isBlank()) return;
        boolean first=sub.getAnalyticsFirstPaymentId()==null;
        if(first) sub.setAnalyticsFirstPaymentId(invoice.paymentId());
        boolean initial=invoice.paymentId().equals(sub.getAnalyticsFirstPaymentId());
        var params=parameters(initial ? "subscription" : "renewal",sub.getPlan(),null,sub.getProvider(),sub.getCurrency());
        BigDecimal amount=invoice.amountPaid()!=null ? invoice.amountPaid() : initial && sub.getWinbackInitialAmount()!=null ? sub.getWinbackInitialAmount():sub.getAmountValue();
        commerce(params,sub.getPlan(),sub.getProvider()+"-"+invoice.paymentId(),amount,initial?sub.getWinbackCouponCode():null);
        enqueue(sub.getProvider()+"-payment-"+invoice.paymentId(),initial?"purchase":"subscription_renewal",
                sub.getProviderEnvironment(),sub.getAnalyticsClientId(),initial?sub.getAnalyticsSessionId():null,params);
    }

    @Transactional
    public void upgrade(UpgradeAdjustment adjustment){
        if(adjustment.getState()!=UpgradeAdjustment.State.COMPLETED) return;
        var params=parameters("upgrade",adjustment.getTargetPlan(),adjustment.getSourcePlan(),PaymentProviderType.EFI,"BRL");
        params.put("recurring_amount",adjustment.getRegularAmount());
        commerce(params,adjustment.getTargetPlan(),adjustment.reference(),adjustment.getDueAmount(),adjustment.getCouponCode());
        enqueue(adjustment.reference(),"purchase",adjustment.getEnvironment(),adjustment.getAnalyticsClientId(),adjustment.getAnalyticsSessionId(),params);
        enqueue(adjustment.reference()+"-completed","upgrade_completed",adjustment.getEnvironment(),adjustment.getAnalyticsClientId(),adjustment.getAnalyticsSessionId(),params);
    }

    /** Plan confirmation alone does not establish an amount charged. */
    @Transactional
    public void planChanged(PaymentSubscription sub, PlanType source){
        var params=parameters("upgrade",sub.getPlan(),source,sub.getProvider(),sub.getCurrency());
        params.put("recurring_amount",sub.getAmountValue());
        enqueue(sub.getProvider()+"-plan-"+sub.getProviderSubscriptionId()+"-"+sub.getPlan(),"upgrade_completed",
                sub.getProviderEnvironment(),sub.getAnalyticsClientId(),sub.getAnalyticsSessionId(),params);
    }

    @Transactional
    public void failure(PaymentSubscription sub,GatewayInvoice invoice){
        if(invoice==null || invoice.paymentId()==null || !Set.of("unpaid","failed","rejected").contains(
                String.valueOf(invoice.paymentStatus()).toLowerCase(Locale.ROOT))) return;
        var params=parameters("subscription",sub.getPlan(),null,sub.getProvider(),sub.getCurrency());
        params.put("failure_stage","provider");
        enqueue(sub.getProvider()+"-failed-"+invoice.paymentId(),"payment_failed",sub.getProviderEnvironment(),
                sub.getAnalyticsClientId(),sub.getAnalyticsSessionId(),params);
    }

    @Transactional
    public void upgradeFailure(UpgradeAdjustment adjustment){
        if(adjustment.getState()!=UpgradeAdjustment.State.FAILED) return;
        var params=parameters("upgrade",adjustment.getTargetPlan(),adjustment.getSourcePlan(),PaymentProviderType.EFI,"BRL");
        params.put("failure_stage","provider");
        enqueue(adjustment.reference()+"-failed","payment_failed",adjustment.getEnvironment(),
                adjustment.getAnalyticsClientId(),adjustment.getAnalyticsSessionId(),params);
    }

    private Map<String,Object> parameters(String flow,PlanType plan,PlanType source,PaymentProviderType provider,String currency){
        var params=new LinkedHashMap<String,Object>();
        params.put("flow_type",flow); params.put("target_plan",plan.name());
        if(source!=null) params.put("source_plan",source.name());
        params.put("payment_provider",provider.name()); params.put("currency",currency);
        return params;
    }
    private void commerce(Map<String,Object> params,PlanType plan,String transaction,BigDecimal amount,String coupon){
        params.put("transaction_id",transaction);params.put("value",amount);
        if(coupon!=null) params.put("coupon",coupon);
        params.put("items",List.of(Map.of("item_id",plan.name(),"item_name",plan.getDisplayName(),"price",amount,"quantity",1)));
    }
    private void enqueue(String key,String name,PaymentEnvironment environment,String clientId,String sessionId,Map<String,Object> params){
        if(environment!=PaymentEnvironment.PRODUCTION || clientId==null || !client.configured() || outbox.existsByEventKey(key)) return;
        if(sessionId!=null) params.put("session_id",sessionId);
        params.put("engagement_time_msec",1);
        var body=Map.of("client_id",clientId,"timestamp_micros",Instant.now().toEpochMilli()*1000,
                "consent",Map.of("ad_user_data","DENIED","ad_personalization","DENIED"),
                "events",List.of(Map.of("name",name,"params",params)));
        try{outbox.save(new Ga4Outbox(key,mapper.writeValueAsString(body)));}
        catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("GA4 payload inválido.");}
    }

    @Scheduled(fixedDelayString="${analytics.ga4.retry-delay-ms:30000}")
    @Transactional
    public void deliverDue(){
        if(!client.configured()) return;
        for(var event:outbox.findDue(Instant.now(),PageRequest.of(0,10))){
            // GA4 only permits backdating within 72 hours; do not shift old sales to today's date.
            if(event.getCreatedAt().isBefore(Instant.now().minus(71,ChronoUnit.HOURS))){event.expire();continue;}
            try{client.send(event.getPayload());event.delivered();}
            catch(RuntimeException ex){
                event.retry();
                // HTTP exceptions can contain the API secret in the URL.
                LoggerFactory.getLogger(getClass()).warn("analytics.ga4.delivery_failed type={}",ex.getClass().getSimpleName());
            }
        }
    }
}
