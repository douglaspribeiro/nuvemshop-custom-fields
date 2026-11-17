package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class UpgradeCouponService {
    private final UpgradeCouponRepository coupons;
    private final UpgradeCouponUseRepository uses;
    private final UpgradeAdjustmentRepository adjustments;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    public UpgradeCouponService(UpgradeCouponRepository coupons,UpgradeCouponUseRepository uses,UpgradeAdjustmentRepository adjustments){
        this.coupons=coupons;this.uses=uses;this.adjustments=adjustments;
    }
    public List<UpgradeCoupon> list(){return coupons.findAllByOrderByCreatedAtDesc();}
    public List<UpgradeCouponUse> recentUses(){return uses.findTop100ByOrderByCreatedAtDesc();}
    public UpgradeCoupon eligible(String code,Long storeId,PaymentEnvironment environment,PlanType target){
        if(code==null || code.isBlank())return null;
        var c=coupons.findByCode(normalize(code)).orElseThrow(UpgradeCouponService::unavailable);
        if (c.getPurpose() != UpgradeCoupon.Purpose.UPGRADE || c.getProvider() != PaymentProviderType.EFI) throw unavailable();
        validate(c,storeId,environment,target);
        return c;
    }
    private void validate(UpgradeCoupon c,Long storeId,PaymentEnvironment environment,PlanType target){
        Instant now=Instant.now();
        if(!c.isEnabled() || c.getEnvironment()!=environment || c.getTargetPlan()!=target
                || (c.getStartsAt()!=null && now.isBefore(c.getStartsAt()))
                || (c.getEndsAt()!=null && !now.isBefore(c.getEndsAt()))
                || (c.getMaxUses()!=null && c.getUsedCount()>=c.getMaxUses())
                || uses.countByCouponIdAndStoreIdAndStatusNot(c.getId(),storeId,UpgradeCouponUse.Status.RELEASED)>=c.getMaxUsesPerStore())
            throw unavailable();
    }
    public UpgradeCoupon firstPaymentEligible(String code, Long storeId, PaymentEnvironment environment, PlanType plan) {
        var coupon = coupons.findByCode(normalize(code)).orElseThrow(UpgradeCouponService::firstPaymentUnavailable);
        if (coupon.getProvider() != PaymentProviderType.EFI || coupon.getPurpose() != UpgradeCoupon.Purpose.FIRST_PAYMENT) throw firstPaymentUnavailable();
        validateFirstPayment(coupon, storeId, environment, plan);
        return coupon;
    }

    @Transactional
    public UpgradeCouponUse reserveFirstPayment(String code, Long storeId, PaymentEnvironment environment,
            PlanType plan, String reference, BigDecimal regular) {
        var coupon = coupons.findByCode(normalize(code)).orElseThrow(UpgradeCouponService::firstPaymentUnavailable);
        // Refresh after obtaining the lock: this entity may already be cached by the checkout preview.
        entityManager.refresh(coupon, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (coupon.getProvider() != PaymentProviderType.EFI || coupon.getPurpose() != UpgradeCoupon.Purpose.FIRST_PAYMENT) throw firstPaymentUnavailable();
        validateFirstPayment(coupon, storeId, environment, plan);
        BigDecimal first = firstAmount(regular, coupon.getDiscountPercent());
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        var use = new UpgradeCouponUse(); use.setCouponId(coupon.getId()); use.setStoreId(storeId);
        use.payment(reference, coupon.getCode(), regular, first);
        return uses.saveAndFlush(use);
    }

    public UpgradeCoupon dodoEligible(String code, Long storeId, PaymentEnvironment environment, PlanType plan) {
        var coupon = coupons.findByCode(normalize(code)).orElseThrow(UpgradeCouponService::firstPaymentUnavailable);
        if (coupon.getProvider() != PaymentProviderType.DODO || coupon.getPurpose() != UpgradeCoupon.Purpose.FIRST_PAYMENT
                || coupon.getNativeDiscountId() == null) throw firstPaymentUnavailable();
        validateFirstPayment(coupon, storeId, environment, plan);
        return coupon;
    }

    @Transactional
    public UpgradeCouponUse reserveDodo(Long couponId, UpgradeCoupon quoted, Long storeId, String reference, BigDecimal regular,
            BigDecimal first, String nativeId, PaymentEnvironment environment) {
        var coupon = coupons.findLocked(couponId).orElseThrow(UpgradeCouponService::firstPaymentUnavailable);
        entityManager.refresh(coupon, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (coupon.getProvider() != PaymentProviderType.DODO || coupon.getPurpose() != UpgradeCoupon.Purpose.FIRST_PAYMENT
                || !Objects.equals(coupon.getNativeDiscountId(), nativeId)
                || !coupon.getCode().equals(quoted.getCode()) || coupon.getDiscountPercent().compareTo(quoted.getDiscountPercent()) != 0)
            throw firstPaymentUnavailable();
        validateFirstPayment(coupon, storeId, environment, quoted.getTargetPlan());
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        var use = new UpgradeCouponUse(); use.setCouponId(coupon.getId()); use.setStoreId(storeId);
        use.payment(reference, coupon.getCode(), regular, first); use.setNativeDiscountId(nativeId);
        return uses.saveAndFlush(use);
    }

    public Optional<UpgradeCouponUse> firstPaymentUse(PaymentSubscription subscription) {
        if (subscription.getExternalReference() == null) return Optional.empty();
        return uses.findByPaymentReference(subscription.getExternalReference()).filter(use ->
                use.getStoreId().equals(subscription.getStoreId())
                && Objects.equals(use.getCouponCode(), subscription.getWinbackCouponCode()));
    }

    @Transactional
    public void releaseFirstPayment(UpgradeCouponUse use) {
        if (use.getStatus() != UpgradeCouponUse.Status.RESERVED) return;
        var coupon = coupons.findLocked(use.getCouponId()).orElseThrow();
        entityManager.refresh(coupon, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        use.setStatus(UpgradeCouponUse.Status.RELEASED);
        coupon.setUsedCount(Math.max(0, coupon.getUsedCount() - 1));
    }

    public static BigDecimal firstAmount(BigDecimal regular, BigDecimal percent) {
        BigDecimal first = regular.multiply(BigDecimal.ONE.subtract(percent.movePointLeft(2)))
                .setScale(2, java.math.RoundingMode.DOWN);
        if (first.signum() <= 0) throw new IllegalArgumentException("O desconto precisa manter um valor maior que zero na primeira mensalidade.");
        return first;
    }

    private void validateFirstPayment(UpgradeCoupon coupon, Long storeId, PaymentEnvironment environment, PlanType plan) {
        try { validate(coupon, storeId, environment, plan); }
        catch (IllegalArgumentException ex) { throw firstPaymentUnavailable(); }
    }

    private static IllegalArgumentException firstPaymentUnavailable() {
        return new IllegalArgumentException("Cupom inválido ou indisponível para a primeira mensalidade deste plano.");
    }

    /** Reserva no início do pagamento, sob o mesmo commit da intenção financeira. */
    @Transactional
    public void reserve(UpgradeAdjustment a){
        if(a.getCouponCode()==null)return;
        if(a.getCouponId()==null)throw new IllegalArgumentException("Revise o upgrade para validar o cupom novamente.");
        var c=coupons.findLocked(a.getCouponId()).orElseThrow(UpgradeCouponService::unavailable);
        if(uses.findByAdjustmentId(a.getId()).isPresent())return;
        if (c.getPurpose() != UpgradeCoupon.Purpose.UPGRADE || c.getProvider() != PaymentProviderType.EFI) throw unavailable();
        validate(c,a.getStoreId(),a.getEnvironment(),a.getTargetPlan());
        if(!c.getCode().equals(a.getCouponCode()) || c.getDiscountPercent().compareTo(a.getCouponPercent())!=0)
            throw new IllegalArgumentException("O cupom mudou. Revise o upgrade antes de pagar.");
        c.setUsedCount(c.getUsedCount()+1);
        coupons.save(c);
        var use=new UpgradeCouponUse();use.setCouponId(c.getId());use.setStoreId(a.getStoreId());use.setAdjustmentId(a.getId());uses.save(use);
    }
    @Transactional
    public void complete(UpgradeAdjustment a){
        if(a.getCouponId()==null)return;
        coupons.findLocked(a.getCouponId()).orElseThrow();
        uses.findByAdjustmentId(a.getId()).filter(u->u.getStatus()==UpgradeCouponUse.Status.RESERVED)
                .ifPresent(u->{u.setStatus(UpgradeCouponUse.Status.USED);uses.save(u);});
    }
    @Transactional
    public void release(UpgradeAdjustment a){
        if(a.getCouponId()==null)return;
        var c=coupons.findLocked(a.getCouponId()).orElseThrow();
        uses.findByAdjustmentId(a.getId()).filter(u->u.getStatus()==UpgradeCouponUse.Status.RESERVED).ifPresent(u->{
            u.setStatus(UpgradeCouponUse.Status.RELEASED);uses.save(u);c.setUsedCount(Math.max(0,c.getUsedCount()-1));coupons.save(c);
        });
    }
    @Transactional
    public UpgradeCoupon save(Long id,String code,BigDecimal percent,PaymentEnvironment environment,PlanType target,
            boolean enabled,Instant starts,Instant ends,Integer maxUses,int perStore){
        return save(id, code, percent, environment, target, enabled, starts, ends, maxUses, perStore, UpgradeCoupon.Purpose.UPGRADE);
    }
    @Transactional
    public UpgradeCoupon save(Long id,String code,BigDecimal percent,PaymentEnvironment environment,PlanType target,
            boolean enabled,Instant starts,Instant ends,Integer maxUses,int perStore,UpgradeCoupon.Purpose purpose){
        return save(id,code,percent,environment,target,enabled,starts,ends,maxUses,perStore,purpose,PaymentProviderType.EFI);
    }
    @Transactional
    public UpgradeCoupon save(Long id,String code,BigDecimal percent,PaymentEnvironment environment,PlanType target,
            boolean enabled,Instant starts,Instant ends,Integer maxUses,int perStore,UpgradeCoupon.Purpose purpose,PaymentProviderType provider){
        String normalized=normalize(code);
        if(percent==null || percent.scale()>2 || percent.signum()<=0 || percent.compareTo(new BigDecimal("100"))>0
                || environment==null || target==null || !target.isBillable()
                || (starts!=null && ends!=null && !starts.isBefore(ends))
                || (maxUses!=null && maxUses<1) || perStore<1)
            throw new IllegalArgumentException("Confira percentual (0,01 a 100), período e limites de utilização.");
        if (purpose == null || (purpose == UpgradeCoupon.Purpose.FIRST_PAYMENT
                && (percent.compareTo(new BigDecimal("100")) >= 0 || normalized.startsWith("VOLTE50-"))))
            throw new IllegalArgumentException("Para a primeira mensalidade, use desconto menor que 100% e um código sem o prefixo VOLTE50-.");
        if (provider != PaymentProviderType.EFI && provider != PaymentProviderType.DODO)
            throw new IllegalArgumentException("Escolha Efí ou Dodo para o cupom.");
        if (provider == PaymentProviderType.DODO && (purpose != UpgradeCoupon.Purpose.FIRST_PAYMENT || normalized.length() < 3))
            throw new IllegalArgumentException("No Dodo, use cupons de primeira mensalidade com pelo menos 3 caracteres.");
        var c=id==null?new UpgradeCoupon():coupons.findLocked(id).orElseThrow(()->new IllegalArgumentException("Cupom não encontrado."));
        if(c.getId()!=null && (!normalized.equals(c.getCode()) || c.getPurpose() != purpose || c.getProvider() != provider || c.getEnvironment() != environment)
                && (c.getProvider() == PaymentProviderType.DODO || uses.existsByCouponId(id) || adjustments.existsByCouponId(id)))
            throw new IllegalArgumentException("Para alterar código, finalidade, gateway ou ambiente de um cupom Dodo ou com histórico, crie outro cupom.");
        var duplicate=coupons.findByCode(normalized);
        if(duplicate.isPresent() && !Objects.equals(duplicate.get().getId(),id))throw new IllegalArgumentException("Já existe um cupom com este código.");
        if(maxUses!=null && maxUses<c.getUsedCount())throw new IllegalArgumentException("O limite não pode ser menor que as utilizações já reservadas/confirmadas.");
        if (c.getProvider() != provider || c.getEnvironment() != environment || !normalized.equals(c.getCode())) c.setNativeDiscountId(null);
        c.setProvider(provider);
        c.setPurpose(purpose);
        c.setCode(normalized);c.setDiscountPercent(percent);c.setEnvironment(environment);c.setTargetPlan(target);c.setEnabled(enabled);
        c.setStartsAt(starts);c.setEndsAt(ends);c.setMaxUses(maxUses);c.setMaxUsesPerStore(purpose == UpgradeCoupon.Purpose.FIRST_PAYMENT ? 1 : perStore);c.setUpdatedAt(Instant.now());
        return coupons.save(c);
    }
    @Transactional
    public boolean delete(Long id){
        var c=coupons.findLocked(id).orElseThrow(()->new IllegalArgumentException("Cupom não encontrado."));
        if(c.getProvider()==PaymentProviderType.DODO || c.getUsedCount()>0 || uses.existsByCouponId(id) || adjustments.existsByCouponId(id)){
            c.setEnabled(false);c.setUpdatedAt(Instant.now());coupons.save(c);return false;
        }
        coupons.delete(c);return true;
    }
    private static String normalize(String code){
        String result=code==null?"":code.strip().toUpperCase(Locale.ROOT);
        if(!result.matches("[A-Z0-9_-]{2,36}"))throw new IllegalArgumentException("Use de 2 a 36 letras, números, hífen ou sublinhado no código.");
        return result;
    }
    private static IllegalArgumentException unavailable(){return new IllegalArgumentException("Cupom inválido ou indisponível para este upgrade.");}
}
