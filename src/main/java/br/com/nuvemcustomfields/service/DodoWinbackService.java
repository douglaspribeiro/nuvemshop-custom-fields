package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.properties.WinbackDiscountProperties;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Durable winback intent, native confirmation, then email claim. No store lock is held across Dodo HTTP. */
@Service
public class DodoWinbackService {
    private final StoreRepository stores;
    private final WinbackCampaignRepository campaigns;
    private final WinbackEmailRepository emails;
    private final WinbackCouponRepository coupons;
    private final PaymentCatalogPriceRepository prices;
    private final PaymentGatewayRouter router;
    private final WinbackDiscountService history;
    private final WinbackDiscountProperties properties;
    private final br.com.nuvemcustomfields.properties.HomologProperties homolog;
    private final TransactionTemplate transaction;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public DodoWinbackService(StoreRepository stores,WinbackCampaignRepository campaigns,WinbackEmailRepository emails,
            WinbackCouponRepository coupons,PaymentCatalogPriceRepository prices,PaymentGatewayRouter router,
            WinbackDiscountService history,WinbackDiscountProperties properties,
            br.com.nuvemcustomfields.properties.HomologProperties homolog,PlatformTransactionManager manager) {
        this.stores=stores;this.campaigns=campaigns;this.emails=emails;this.coupons=coupons;this.prices=prices;
        this.router=router;this.history=history;this.properties=properties;this.homolog=homolog;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private Optional<DodoGateway> gateway(Store store) {
        if (store==null || store.isErasurePending() || store.getAccessBlockedAt()!=null
                || store.getStoreCountryCode()==null || "BR".equalsIgnoreCase(store.getStoreCountryCode())) return Optional.empty();
        // Evaluate the route that this same store would use after reinstalling; never reactivate the real store.
        var reinstalled=new Store();reinstalled.setStoreId(store.getStoreId());
        reinstalled.setStoreCountryCode(store.getStoreCountryCode());reinstalled.setStoreCurrency(store.getStoreCurrency());
        return router.forStore(reinstalled).filter(g->g instanceof DodoGateway).map(g->(DodoGateway)g)
                .filter(g->properties.enabled() && (g.environment()==PaymentEnvironment.SANDBOX || properties.allowProduction()) && g.operational());
    }
    private boolean eligible(WinbackCampaign campaign,Store store) {
        return campaign!=null && !campaign.isPaidBeforeDeparture() && campaign.getRespondedAt()!=null
                && campaign.getOptedOutAt()==null && !campaigns.existsByStoreIdAndOptedOutAtIsNotNull(store.getStoreId())
                && !history.hasPaidHistory(store.getStoreId());
    }
    private boolean mayEmail(WinbackCampaign campaign,Store store) {
        return store!=null && !store.isActive() && !store.isErasurePending() && store.getAccessBlockedAt()==null
                && campaign!=null && Objects.equals(store.getUninstalledAt(),campaign.getUninstalledAt())
                && campaign.getReinstalledAt()==null && campaign.getOptedOutAt()==null
                && !campaigns.existsByStoreIdAndOptedOutAtIsNotNull(store.getStoreId());
    }

    /** Called under the same store lock as the recorded reply. This writes only local intent. */
    @Transactional
    public Optional<WinbackCoupon> issue(WinbackCampaign campaign,Store store) {
        var route=gateway(store);
        if (!homolog.allows(store.getStoreId()) || route.isEmpty() || !mayEmail(campaign,store) || !eligible(campaign,store)) return Optional.empty();
        var gateway=route.get();
        var previous=coupons.findByStoreId(store.getStoreId());
        if (previous.isPresent()) return previous.filter(c->c.getCampaignId().equals(campaign.getId())
                && c.getProvider()==gateway.provider() && c.getProviderEnvironment()==gateway.environment()
                && c.getUsedAt()==null && c.getExpiresAt().isAfter(Instant.now()));
        var coupon=new WinbackCoupon(store.getStoreId(),campaign.getId());coupon.useDodo(gateway.provider(),gateway.environment());
        return Optional.of(coupons.saveAndFlush(coupon));
    }

    @Transactional
    public boolean requiresConfirmation(WinbackCampaign campaign,Store store) {
        return issue(campaign,store).filter(c->c.getNativeDiscountId()==null || c.getNativeSyncError()!=null).isPresent();
    }

    /** Invoked by the follow-up worker before prepareFollowup commits the SENDING claim. */
    public boolean prepareForEmail(String emailId) {
        Snapshot snapshot=transaction.execute(status->{
            var mail=emails.findById(emailId).orElse(null);
            if (mail==null || !"FOLLOWUP".equals(mail.getStep()) || !"PREPARED".equals(mail.getStatus())) return null;
            var campaign=campaigns.findById(mail.getCampaignId()).orElse(null);
            if (campaign==null) return null;
            var store=stores.findByStoreIdForUpdate(campaign.getStoreId()).orElse(null);
            if (store==null) return null;
            entityManager.refresh(campaign,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(mail,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (!"PREPARED".equals(mail.getStatus())) return null;
            if (!mayEmail(campaign,store)) {mail.status("CANCELLED");return null;}
            var coupon=issue(campaign,store).orElse(null);
            if (coupon==null) return null;
            return new Snapshot(coupon,gateway(store).orElseThrow(),products(coupon,store),coupon.getNativeConfirmedAt());
        });
        if (snapshot==null) return true;
        var coupon=snapshot.coupon();
        if (coupon.getNativeSyncAfter()!=null && coupon.getNativeSyncAfter().isAfter(Instant.now())) return false;
        try {
            JsonNode remote=snapshot.gateway().synchronizeWinbackDiscount(coupon,snapshot.products());
            validate(remote,coupon,snapshot.products());
            return Boolean.TRUE.equals(transaction.execute(status->{
                var store=stores.findByStoreIdForUpdate(coupon.getStoreId()).orElse(null);
                var current=coupons.findById(coupon.getCode()).orElse(null);
                var mail=emails.findById(emailId).orElse(null);
                if (store==null || current==null || mail==null) return false;
                current.nativeConfirmed(DodoGateway.required(remote,"discount_id"));coupons.saveAndFlush(current);
                var campaign=campaigns.findById(current.getCampaignId()).orElse(null);
                if (!mayEmail(campaign,store)) {mail.status("CANCELLED");return false;}
                return "PREPARED".equals(mail.getStatus());
            }));
        } catch (RuntimeException ex) {
            transaction.executeWithoutResult(status->{
                if (stores.findByStoreIdForUpdate(coupon.getStoreId()).isEmpty()) return;
                var current=coupons.findById(coupon.getCode()).orElse(null);
                var mail=emails.findById(emailId).orElse(null);
                if (current!=null && mail!=null && "PREPARED".equals(mail.getStatus())
                        && Objects.equals(current.getNativeConfirmedAt(),snapshot.confirmedAt())) {
                    current.nativeFailed();coupons.saveAndFlush(current);
                }
            });
            throw ex;
        }
    }

    public boolean readyForOffer(WinbackCoupon coupon,Store store) {
        var route=gateway(store);
        return coupon!=null && coupon.getProvider().isDodo() && coupon.getNativeDiscountId()!=null && coupon.getNativeConfirmedAt()!=null && coupon.getNativeSyncError()==null
                && coupon.getUsedAt()==null && coupon.getExpiresAt().isAfter(Instant.now()) && route.isPresent()
                && route.get().provider()==coupon.getProvider() && route.get().environment()==coupon.getProviderEnvironment()
                && eligible(campaigns.findById(coupon.getCampaignId()).orElse(null),store);
    }
    public boolean isCode(String code) {
        return code!=null && coupons.findById(code.strip().toUpperCase(Locale.ROOT)).isPresent();
    }
    public String automaticCode(Store store,DodoGateway gateway) {
        return coupons.findByStoreId(store.getStoreId()).filter(c->usable(c,store,gateway)).map(WinbackCoupon::getCode).orElse(null);
    }
    private boolean usable(WinbackCoupon coupon,Store store,DodoGateway gateway) {
        var campaign=campaigns.findById(coupon.getCampaignId()).orElse(null);
        return store.isActive() && coupon.getStoreId().equals(store.getStoreId()) && readyForOffer(coupon,store)
                && coupon.getProvider()==gateway.provider() && coupon.getProviderEnvironment()==gateway.environment()
                && campaign!=null && campaign.getReinstalledAt()!=null;
    }
    public DodoCouponService.Quote quote(Store store,PlanType plan,String code,DodoGateway gateway) {
        var coupon=coupons.findById(code.strip().toUpperCase(Locale.ROOT)).filter(c->usable(c,store,gateway))
                .orElseThrow(()->new IllegalArgumentException("Cupom de reconquista indisponível para esta loja ou ambiente."));
        var remote=gateway.discountByCode(coupon.getCode()).orElseThrow(()->new IllegalArgumentException("Cupom ainda não confirmado no Dodo."));
        validate(remote,coupon,products(coupon,store));
        if (!coupon.getNativeDiscountId().equals(DodoGateway.required(remote,"discount_id")) || remote.path("times_used").asInt()>=1)
            throw new IllegalArgumentException("O cupom Dodo já foi utilizado ou precisa ser conciliado.");
        BigDecimal first=gateway.previewFirstAmount(store,plan,coupon.getCode());
        return new DodoCouponService.Quote(null,null,coupon.getNativeDiscountId(),plan,gateway.environment(),gateway.currency(store),
                first,gateway.amount(store,plan),coupon.getCode());
    }
    @Transactional
    public void reserve(DodoCouponService.Quote quote,Store store,String reference,DodoGateway gateway) {
        var coupon=coupons.findById(quote.winbackCode()).filter(c->usable(c,store,gateway))
                .orElseThrow(()->new IllegalArgumentException("Cupom de reconquista indisponível."));
        if (!Objects.equals(coupon.getNativeDiscountId(),quote.nativeId())) throw new IllegalArgumentException("O cupom mudou. Reabra o resumo.");
        coupon.reserve(reference,quote.regular(),quote.first());coupons.saveAndFlush(coupon);
    }
    public String reservedCode(String reference) {
        return reference==null?null:coupons.findByExternalReference(reference).filter(c->c.getProvider().isDodo()).map(WinbackCoupon::getCode).orElse(null);
    }
    @Transactional
    public void confirm(PaymentSubscription local,GatewaySubscription remote,GatewayInvoice invoice) {
        var coupon=coupons.findByExternalReference(local.getExternalReference()).filter(c->c.getProvider()==local.getProvider()
                && c.getProviderEnvironment()==local.getProviderEnvironment() && c.getStoreId().equals(local.getStoreId())).orElse(null);
        if (coupon==null || coupon.getUsedAt()!=null) return;
        if (invoice!=null && invoice.approved() && Objects.equals(invoice.subscriptionId(),remote.id())) {
            coupon.subscription(remote.id());coupon.firstPayment(invoice.paymentId());coupon.used();
            campaigns.findById(coupon.getCampaignId()).ifPresent(WinbackCampaign::converted);
        } else if (Set.of("cancelled","canceled","expired","failed").contains(remote.status())) coupon.stopped();
    }
    private List<String> products(WinbackCoupon coupon,Store store) {
        return prices.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseOrderByPlan(coupon.getProvider(),coupon.getProviderEnvironment(),store.getStoreCountryCode())
                .stream().filter(p->p.isEnabled() && p.isRecurring() && p.isValidated()).map(PaymentCatalogPrice::getProviderPriceId).distinct().sorted().toList();
    }
    private static void validate(JsonNode remote,WinbackCoupon coupon,List<String> products) {
        var restricted=new HashSet<String>();remote.path("restricted_to").forEach(p->restricted.add(p.asText()));
        String expiry=DodoGateway.text(remote,"expires_at");boolean validDate=false;
        try {validDate=expiry!=null && Instant.parse(expiry).equals(coupon.getExpiresAt());}catch(java.time.format.DateTimeParseException ignored){}
        if (products.isEmpty() || !restricted.equals(new HashSet<>(products)) || !validDate
                || !coupon.getCode().equals(DodoGateway.text(remote,"code")) || !"percentage".equals(DodoGateway.text(remote,"type"))
                || remote.path("amount").asInt()!=5000 || remote.path("subscription_cycles").asInt()!=1
                || remote.path("usage_limit").asInt()!=1 || remote.path("times_used").asInt()>=1 || !"any".equals(DodoGateway.text(remote,"customer_eligibility"))
                || (!remote.path("per_customer_usage_limit").isNull() && !remote.path("per_customer_usage_limit").isMissingNode())
                || !remote.path("preserve_on_plan_change").isBoolean() || remote.path("preserve_on_plan_change").asBoolean()
                || DodoGateway.text(remote,"starts_at")!=null
                || !coupon.getCode().equals(DodoGateway.text(remote.path("metadata"),"ncf_winback_coupon"))
                || !coupon.getCampaignId().equals(DodoGateway.text(remote.path("metadata"),"ncf_winback_campaign")))
            throw new IllegalArgumentException("O Dodo não confirmou o cupom de reconquista de 50% por uma mensalidade.");
    }
    private record Snapshot(WinbackCoupon coupon,DodoGateway gateway,List<String> products,Instant confirmedAt){}
}
