package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class DodoCouponService {
    private final UpgradeCouponService catalog;
    private final UpgradeCouponRepository coupons;
    private final UpgradeCouponUseRepository uses;
    private final PaymentCatalogPriceRepository prices;
    private final PaymentGatewayRouter router;
    private final DodoLiveGateway live;
    private final DodoTestGateway test;
    private final WinbackDiscountService history;
    private final TransactionTemplate transaction;
    private DodoWinbackService winback;
    @org.springframework.beans.factory.annotation.Autowired
    public void setWinback(DodoWinbackService service) { winback=service; }
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public DodoCouponService(UpgradeCouponService catalog, UpgradeCouponRepository coupons, UpgradeCouponUseRepository uses,
            PaymentCatalogPriceRepository prices, PaymentGatewayRouter router, DodoLiveGateway live, DodoTestGateway test,
            WinbackDiscountService history, PlatformTransactionManager manager) {
        this.catalog=catalog; this.coupons=coupons; this.uses=uses; this.prices=prices; this.router=router;
        this.live=live; this.test=test; this.history=history; transaction=new TransactionTemplate(manager);
    }

    public DodoGateway gateway(Store store) {
        var gateway=router.requireForStore(store);
        if (!(gateway instanceof DodoGateway dodo)) throw new IllegalArgumentException("O pagamento desta loja não usa Dodo.");
        return dodo;
    }

    /** Saving an enabled Dodo coupon is the explicit operator action creating/updating its native discount. */
    public void synchronize(UpgradeCoupon coupon, boolean requestedEnabled) {
        if (!requestedEnabled) return;
        String snapshot=fingerprint(coupon);
        DodoGateway gateway=coupon.getEnvironment()==PaymentEnvironment.SANDBOX?test:live;
        var products=products(coupon);
        if (products.isEmpty()) throw new IllegalArgumentException("Cadastre e valide os produtos Dodo do plano antes de ativar o cupom.");
        JsonNode remote=gateway.synchronizeDiscount(coupon, products);
        validateRemote(remote,coupon,products.getFirst(),false);
        transaction.executeWithoutResult(status->{
            var current=coupons.findLocked(coupon.getId()).orElseThrow();
            entityManager.refresh(current,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (!fingerprint(current).equals(snapshot)) throw new IllegalArgumentException("O cupom foi alterado durante a sincronização. Revise e salve novamente.");
            current.setNativeDiscountId(DodoGateway.required(remote,"discount_id")); current.setEnabled(true);
            coupons.saveAndFlush(current);
        });
    }

    private List<String> products(UpgradeCoupon coupon) {
        var provider=coupon.getEnvironment()==PaymentEnvironment.SANDBOX?PaymentProviderType.DODO_TEST:PaymentProviderType.DODO;
        return prices.findAll().stream().filter(p->p.getProvider()==provider && p.getEnvironment()==coupon.getEnvironment()
                && p.getPlan()==coupon.getTargetPlan() && p.isEnabled() && p.isRecurring() && p.isValidated())
                .map(PaymentCatalogPrice::getProviderPriceId).distinct().sorted().toList();
    }

    public Quote quote(Store store, PlanType plan, String code) {
        DodoGateway gateway=gateway(store);
        if (!gateway.planAvailable(store,plan)) throw new IllegalArgumentException("Plano Dodo indisponível para esta loja.");
        BigDecimal regular=gateway.amount(store,plan);
        if ((code==null || code.isBlank()) && winback!=null) code=winback.automaticCode(store,gateway);
        if (code==null || code.isBlank()) return new Quote(null,null,null,plan,gateway.environment(),gateway.currency(store),regular,regular);
        requireFirstPayment(store);
        if (winback!=null && winback.isCode(code)) return winback.quote(store,plan,code,gateway);
        var coupon=catalog.dodoEligible(code,store.getStoreId(),gateway.environment(),plan);
        var remote=gateway.discountByCode(coupon.getCode()).orElseThrow(()->new IllegalArgumentException("Cupom não encontrado no Dodo."));
        validateRemote(remote,coupon,gateway.priceId(store,plan),true);
        if (!coupon.getNativeDiscountId().equals(DodoGateway.required(remote,"discount_id")))
            throw new IllegalArgumentException("O vínculo do cupom Dodo mudou. Contate o suporte.");
        BigDecimal first=gateway.previewFirstAmount(store,plan,coupon.getCode());
        // Detach the definition snapshot from the entity later refreshed under a reservation lock.
        var snapshot=new UpgradeCoupon(); snapshot.setCode(coupon.getCode()); snapshot.setDiscountPercent(coupon.getDiscountPercent());
        snapshot.setTargetPlan(coupon.getTargetPlan()); snapshot.setEnvironment(coupon.getEnvironment());
        return new Quote(coupon.getId(),snapshot,coupon.getNativeDiscountId(),plan,gateway.environment(),gateway.currency(store),first,regular);
    }

    public void requireFirstPayment(Store store) {
        if (!store.isActive() || store.isErasurePending() || store.getAccessBlockedAt()!=null || history.hasPaidHistory(store.getStoreId()))
            throw new IllegalArgumentException("O cupom Dodo está disponível somente no primeiro pagamento da loja.");
    }

    public void reserve(Quote quote, Store store, String reference) {
        if (!quote.discounted()) return;
        requireFirstPayment(store);
        if (quote.winbackCode()!=null && winback!=null) {winback.reserve(quote,store,reference,gateway(store));return;}
        // The locked definition must match the immutable preview snapshot, not a refreshed entity alias.
        catalog.reserveDodo(quote.couponId(), quote.definition(),store.getStoreId(),reference,quote.regular(),quote.first(),quote.nativeId(),quote.environment());
    }

    public String reservedCode(String reference) {
        if (reference==null) return null;
        return uses.findByPaymentReference(reference).filter(u->u.getNativeDiscountId()!=null).map(UpgradeCouponUse::getCouponCode)
                .orElseGet(()->winback==null?null:winback.reservedCode(reference));
    }

    @Transactional
    public void confirm(PaymentSubscription local, GatewaySubscription remote, GatewayInvoice invoice) {
        if (!local.getProvider().isDodo()) return;
        if (winback!=null) winback.confirm(local,remote,invoice);
        var use=uses.findByPaymentReference(local.getExternalReference()).filter(u->u.getNativeDiscountId()!=null
                && u.getStoreId().equals(local.getStoreId())).orElse(null);
        if (use==null || use.getStatus()!=UpgradeCouponUse.Status.RESERVED) return;
        if (invoice!=null && invoice.approved() && Objects.equals(invoice.subscriptionId(),remote.id())) {
            if (use.getSubscriptionId()!=null && !remote.id().equals(use.getSubscriptionId()))
                throw new IllegalArgumentException("Cupom vinculado a outra assinatura Dodo.");
            use.subscription(remote.id()); use.firstPayment(invoice.paymentId()); use.used();
        } else if (Set.of("cancelled","canceled","expired","failed").contains(remote.status())) {
            use.stopped(); catalog.releaseFirstPayment(use);
        }
    }

    private void validateRemote(JsonNode remote, UpgradeCoupon coupon, String productId, boolean checkUsage) {
        var restricted=remote.path("restricted_to"); var allowed=products(coupon);
        boolean validProducts=restricted.isArray() && !restricted.isEmpty(); boolean contains=false;
        for (var product:restricted) {
            validProducts &= allowed.contains(product.asText()); contains |= productId.equals(product.asText());
        }
        if (!coupon.getCode().equals(DodoGateway.text(remote,"code")) || !"percentage".equals(DodoGateway.text(remote,"type"))
                || remote.path("amount").asInt(-1)!=coupon.getDiscountPercent().movePointRight(2).intValueExact()
                || remote.path("subscription_cycles").asInt(-1)!=1
                || (!remote.path("per_customer_usage_limit").isNull() && !remote.path("per_customer_usage_limit").isMissingNode())
                || !"any".equals(DodoGateway.text(remote,"customer_eligibility"))
                || !remote.path("preserve_on_plan_change").isBoolean() || remote.path("preserve_on_plan_change").asBoolean()
                || !coupon.getId().toString().equals(DodoGateway.text(remote.path("metadata"),"ncf_coupon_id"))
                || !validProducts || !contains || !sameDate(remote,"starts_at",coupon.getStartsAt()) || !sameDate(remote,"expires_at",coupon.getEndsAt())
                || (coupon.getMaxUses()==null ? !remote.path("usage_limit").isNull() && !remote.path("usage_limit").isMissingNode()
                    : remote.path("usage_limit").asInt(-1)!=coupon.getMaxUses()))
            throw new IllegalArgumentException("O cupom Dodo diverge do cadastro ou não está limitado à primeira mensalidade. Salve-o novamente no backoffice.");
        if (checkUsage && coupon.getMaxUses()!=null && remote.path("times_used").asInt()>=coupon.getMaxUses())
            throw new IllegalArgumentException("O limite de utilizações do cupom Dodo foi atingido.");
    }
    private static boolean sameDate(JsonNode node,String key,Instant expected) {
        String value=DodoGateway.text(node,key);
        try { return Objects.equals(value==null?null:Instant.parse(value),expected); }
        catch (java.time.format.DateTimeParseException ex) { return false; }
    }
    private static String fingerprint(UpgradeCoupon c) {
        return List.of(c.getCode(),c.getProvider(),c.getEnvironment(),c.getTargetPlan(),c.getPurpose(),c.getDiscountPercent().stripTrailingZeros().toPlainString(),
                String.valueOf(c.getStartsAt()),String.valueOf(c.getEndsAt()),String.valueOf(c.getMaxUses())).toString();
    }
    public record Quote(Long couponId, UpgradeCoupon definition, String nativeId, PlanType plan,
                        PaymentEnvironment environment,String currency,BigDecimal first,BigDecimal regular,String winbackCode) {
        public Quote(Long couponId,UpgradeCoupon definition,String nativeId,PlanType plan,PaymentEnvironment environment,
                String currency,BigDecimal first,BigDecimal regular) {
            this(couponId,definition,nativeId,plan,environment,currency,first,regular,null);
        }
        public boolean discounted() {return couponId!=null || winbackCode!=null;}
        public String code() {return winbackCode!=null?winbackCode:definition==null?null:definition.getCode();}
    }
}
