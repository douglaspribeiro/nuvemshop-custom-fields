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
    public UpgradeCouponService(UpgradeCouponRepository coupons,UpgradeCouponUseRepository uses,UpgradeAdjustmentRepository adjustments){
        this.coupons=coupons;this.uses=uses;this.adjustments=adjustments;
    }
    public List<UpgradeCoupon> list(){return coupons.findAllByOrderByCreatedAtDesc();}
    public List<UpgradeCouponUse> recentUses(){return uses.findTop100ByOrderByCreatedAtDesc();}
    public UpgradeCoupon eligible(String code,Long storeId,PaymentEnvironment environment,PlanType target){
        if(code==null || code.isBlank())return null;
        var c=coupons.findByCode(normalize(code)).orElseThrow(UpgradeCouponService::unavailable);
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
    /** Reserva no início do pagamento, sob o mesmo commit da intenção financeira. */
    @Transactional
    public void reserve(UpgradeAdjustment a){
        if(a.getCouponCode()==null)return;
        if(a.getCouponId()==null)throw new IllegalArgumentException("Revise o upgrade para validar o cupom novamente.");
        var c=coupons.findLocked(a.getCouponId()).orElseThrow(UpgradeCouponService::unavailable);
        if(uses.findByAdjustmentId(a.getId()).isPresent())return;
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
        String normalized=normalize(code);
        if(percent==null || percent.scale()>2 || percent.signum()<=0 || percent.compareTo(new BigDecimal("100"))>0
                || environment==null || target==null || !target.isBillable()
                || (starts!=null && ends!=null && !starts.isBefore(ends))
                || (maxUses!=null && maxUses<1) || perStore<1)
            throw new IllegalArgumentException("Confira percentual (0,01 a 100), período e limites de utilização.");
        var c=id==null?new UpgradeCoupon():coupons.findLocked(id).orElseThrow(()->new IllegalArgumentException("Cupom não encontrado."));
        if(c.getId()!=null && (!normalized.equals(c.getCode()))
                && (uses.existsByCouponId(id) || adjustments.existsByCouponId(id)))
            throw new IllegalArgumentException("O código de um cupom com histórico não pode ser alterado.");
        var duplicate=coupons.findByCode(normalized);
        if(duplicate.isPresent() && !Objects.equals(duplicate.get().getId(),id))throw new IllegalArgumentException("Já existe um cupom com este código.");
        if(maxUses!=null && maxUses<c.getUsedCount())throw new IllegalArgumentException("O limite não pode ser menor que as utilizações já reservadas/confirmadas.");
        c.setCode(normalized);c.setDiscountPercent(percent);c.setEnvironment(environment);c.setTargetPlan(target);c.setEnabled(enabled);
        c.setStartsAt(starts);c.setEndsAt(ends);c.setMaxUses(maxUses);c.setMaxUsesPerStore(perStore);c.setUpdatedAt(Instant.now());
        return coupons.save(c);
    }
    @Transactional
    public boolean delete(Long id){
        var c=coupons.findLocked(id).orElseThrow(()->new IllegalArgumentException("Cupom não encontrado."));
        if(c.getUsedCount()>0 || uses.existsByCouponId(id) || adjustments.existsByCouponId(id)){
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
