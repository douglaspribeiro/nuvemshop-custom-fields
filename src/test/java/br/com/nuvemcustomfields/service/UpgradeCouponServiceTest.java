package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class UpgradeCouponServiceTest {
    @Autowired UpgradeCouponService service;
    @Autowired UpgradeCouponRepository coupons;
    @Autowired UpgradeCouponUseRepository uses;
    final List<Long> created=new ArrayList<>();
    UpgradeCoupon create(Integer max,int perStore){
        var c=service.save(null,"TEST_"+UUID.randomUUID().toString().substring(0,12),new BigDecimal("25"),
                PaymentEnvironment.PRODUCTION,PlanType.PREMIUM_ULTRA,true,null,null,max,perStore);
        created.add(c.getId());return c;
    }
    @AfterEach void cleanup(){
        for(Long id:created){uses.deleteAll(uses.findByCouponId(id));coupons.deleteById(id);}
    }
    UpgradeAdjustment adjustment(UpgradeCoupon c,Long store){
        var a=new UpgradeAdjustment();a.setStoreId(store);a.setCouponId(c.getId());a.setCouponCode(c.getCode());
        a.setCouponPercent(c.getDiscountPercent());a.setEnvironment(c.getEnvironment());a.setTargetPlan(c.getTargetPlan());return a;
    }
    @Test void eligibilityUsesDatabasePercentAndNeverConsumesPreview(){
        var c=create(3,1);
        var selected=service.eligible(" "+c.getCode().toLowerCase(Locale.ROOT)+" ",1L,PaymentEnvironment.PRODUCTION,PlanType.PREMIUM_ULTRA);
        assertThat(selected.getDiscountPercent()).isEqualByComparingTo("25");
        assertThat(coupons.findById(c.getId()).orElseThrow().getUsedCount()).isZero();
        assertThat(uses.findByCouponId(c.getId())).isEmpty();
        assertThat(service.eligible(null,1L,PaymentEnvironment.PRODUCTION,PlanType.PREMIUM_ULTRA)).isNull();
        assertThatThrownBy(()->service.eligible("NOT_REGISTERED",1L,PaymentEnvironment.PRODUCTION,PlanType.PREMIUM_ULTRA)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void invalidEnvironmentPlanDatesAndDisabledAreRejected(){
        var c=create(3,1);
        assertThatThrownBy(()->service.eligible(c.getCode(),1L,PaymentEnvironment.SANDBOX,PlanType.PREMIUM_ULTRA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.eligible(c.getCode(),1L,PaymentEnvironment.PRODUCTION,PlanType.PREMIUM_PLUS)).isInstanceOf(IllegalArgumentException.class);
        c.setStartsAt(Instant.now().plusSeconds(120));coupons.save(c);
        assertUnavailable(c);
        c.setStartsAt(null);c.setEndsAt(Instant.now().minusSeconds(1));coupons.save(c);assertUnavailable(c);
        c.setEndsAt(null);c.setEnabled(false);coupons.save(c);assertUnavailable(c);
    }
    private void assertUnavailable(UpgradeCoupon c){
        assertThatThrownBy(()->service.eligible(c.getCode(),1L,PaymentEnvironment.PRODUCTION,PlanType.PREMIUM_ULTRA)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void reservationIsIdempotentAndEnforcesStoreAndTotalLimits(){
        var c=create(2,1);var a=adjustment(c,1L);
        service.reserve(a);service.reserve(a);
        assertThat(coupons.findById(c.getId()).orElseThrow().getUsedCount()).isEqualTo(1);
        assertThatThrownBy(()->service.reserve(adjustment(c,1L))).isInstanceOf(IllegalArgumentException.class);
        service.reserve(adjustment(c,2L));
        assertThatThrownBy(()->service.reserve(adjustment(c,3L))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void confirmedFailureReleasesOnceAndCompletionKeepsUsage(){
        var c=create(1,1);var a=adjustment(c,1L);service.reserve(a);
        service.release(a);service.release(a);
        assertThat(coupons.findById(c.getId()).orElseThrow().getUsedCount()).isZero();
        assertThat(uses.findByAdjustmentId(a.getId()).orElseThrow().getStatus()).isEqualTo(UpgradeCouponUse.Status.RELEASED);
        var next=adjustment(c,1L);service.reserve(next);service.complete(next);service.complete(next);service.release(next);
        assertThat(coupons.findById(c.getId()).orElseThrow().getUsedCount()).isEqualTo(1);
        assertThat(uses.findByAdjustmentId(next.getId()).orElseThrow().getStatus()).isEqualTo(UpgradeCouponUse.Status.USED);
    }
    @Test void couponChangesBetweenPreviewAndPaymentPreventOldDiscount(){
        var c=create(3,1);var a=adjustment(c,1L);
        service.save(c.getId(),c.getCode(),new BigDecimal("10"),c.getEnvironment(),c.getTargetPlan(),true,null,null,3,1);
        assertThatThrownBy(()->service.reserve(a)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mudou");
        assertThat(coupons.findById(c.getId()).orElseThrow().getUsedCount()).isZero();
    }
    @Test void crudPreservesHistoryAndRejectsInvalidValuesAndDuplicateCode(){
        var c=create(2,1);service.reserve(adjustment(c,1L));
        assertThatThrownBy(()->service.save(c.getId(),"RENAMED",new BigDecimal("25"),c.getEnvironment(),c.getTargetPlan(),true,null,null,2,1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.save(null,c.getCode(),new BigDecimal("25"),c.getEnvironment(),c.getTargetPlan(),true,null,null,2,1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.save(null,"INVALID",new BigDecimal("101"),c.getEnvironment(),c.getTargetPlan(),true,null,null,2,1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.delete(c.getId())).isFalse();assertThat(coupons.findById(c.getId()).orElseThrow().isEnabled()).isFalse();
        var unused=create(null,1);assertThat(service.delete(unused.getId())).isTrue();created.remove(unused.getId());
    }
    @Test void concurrentStoresCannotReserveTheLastSlotTwice() throws Exception {
        var c=create(1,1);var barrier=new CyclicBarrier(2);
        var pool=Executors.newFixedThreadPool(2);
        try{
            var tasks=List.of(1L,2L).stream().map(id->pool.submit(()->{
                barrier.await(5,TimeUnit.SECONDS);
                try{service.reserve(adjustment(c,id));return true;}catch(IllegalArgumentException ex){return false;}
            })).toList();
            int success=0;for(var result:tasks)if(result.get(10,TimeUnit.SECONDS))success++;
            assertThat(success).isEqualTo(1);
            assertThat(coupons.findById(c.getId()).orElseThrow().getUsedCount()).isEqualTo(1);
            assertThat(uses.findByCouponId(c.getId())).hasSize(1);
        }finally{pool.shutdownNow();}
    }
}
