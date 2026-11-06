package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.controller.PublicPersonalizationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockHttpServletRequest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class ConfigurationPlanIntegrationTest {
    @Autowired ConfigurationPlanService service;
    @Autowired ConfigurationPlanPolicy policy;
    @Autowired PersonalizationRuleRepository rules;
    @Autowired PersonalizationFieldRepository fields;
    @Autowired StoreRepository stores;
    @Autowired ConfigurationPauseEmailRepository emails;
    @Autowired PublicPersonalizationController storefront;
    @Autowired EntityManager em;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired ConfigurationPauseEmailDispatcher dispatcher;
    @Autowired PlanLimitService limits;

    Store store() {
        var s=new Store(); s.setStoreId(9900400L); s.setPlan(PlanType.FREE); s.setStoreCountryCode("BR");
        return stores.saveAndFlush(s);
    }
    PersonalizationRule rule(Store s, long product, int count) {
        var r=new PersonalizationRule(); r.setStoreId(s.getStoreId()); r.setProductId(product); r.setProductName("Produto "+product);
        r=rules.saveAndFlush(r);
        for(int i=0;i<count;i++) {
            var f=new PersonalizationField(); f.setRule(r); f.setLabel("Nome "+i); f.setFieldType(FieldType.TEXT);
            fields.saveAndFlush(f); r.getFields().add(f);
        }
        return r;
    }
    boolean visible(Store s, long product) {
        return storefront.getFields(s.getStoreId(), product, "/test", new MockHttpServletRequest()).enabled();
    }
    long notices(Store s) { return emails.findAll().stream().filter(e -> e.getStoreId().equals(s.getStoreId())).count(); }

    @Test void freeKeepsOldestWholeEligibleRuleAndPersistsEvidenceWithoutDeletingFields() {
        var s=store(); var complex=rule(s, 10, 2); var kept=rule(s, 20, 1); var extra=rule(s, 30, 1);
        service.reconcile(s.getStoreId()); em.flush();
        assertThat(complex.isPlanPaused()).isTrue(); assertThat(kept.isPlanPaused()).isFalse(); assertThat(extra.isPlanPaused()).isTrue();
        assertThat(extra.isEnabled()).isTrue(); assertThat(fields.countByStoreId(s.getStoreId())).isEqualTo(4);
        assertThat(kept.getPlanEligibilityEvidence()).contains("NC04/v1", "freeEligible=true", "freeAsset=", "ruleId="+kept.getId());
        assertThat(complex.getPlanEligibilityEvidence()).contains("freeEligible=false");
        assertThat(kept.getPlanEligibilityEvaluatedAt()).isNotNull();
        assertThat(visible(s,10)).isFalse(); assertThat(visible(s,20)).isTrue(); assertThat(visible(s,30)).isFalse();
        var evaluated=kept.getPlanEligibilityEvaluatedAt(); service.reconcile(s.getStoreId());
        assertThat(notices(s)).isEqualTo(1); assertThat(kept.getPlanEligibilityEvaluatedAt()).isEqualTo(evaluated);
        var notice=dispatcher.preview(emails.findAll().getFirst());
        assertThat(notice.body()).contains("2 produto(s)", "continuam salvos", "20");
    }
    @Test void restoresOnlyAutomaticPausesAndRespectsNewPaidLimits() {
        var s=store(); var kept=rule(s,10,1); var automatic=rule(s,20,1); var manual=rule(s,30,1);
        var tooLarge=rule(s,40,4); manual.setEnabled(false); rules.saveAndFlush(manual);
        service.reconcile(s.getStoreId());
        assertThat(automatic.isPlanPaused()).isTrue(); assertThat(manual.isPlanPaused()).isFalse();
        automatic.setEnabled(false); s.setPlan(PlanType.PREMIUM); stores.saveAndFlush(s);
        service.reconcile(s.getStoreId());
        assertThat(automatic.isEnabled()).isFalse(); assertThat(manual.isEnabled()).isFalse(); assertThat(tooLarge.isPlanPaused()).isTrue();
        automatic.setEnabled(true); service.reconcile(s.getStoreId());
        assertThat(automatic.isPlanPaused()).isFalse(); assertThat(visible(s,20)).isTrue();
        s.setPlan(PlanType.PREMIUM_ULTRA); service.reconcile(s.getStoreId());
        assertThat(tooLarge.isPlanPaused()).isFalse(); assertThat(manual.isEnabled()).isFalse(); assertThat(kept.isEnabled()).isTrue();
    }
    @Test void noEligibleRuleMeansAllPausedAndImageRuleIsNotPartiallyShown() {
        var s=store(); var image=rule(s,10,1); var large=rule(s,20,2);
        image.getFields().getFirst().setFieldType(FieldType.IMAGE_SELECT); fields.saveAndFlush(image.getFields().getFirst());
        service.reconcile(s.getStoreId());
        assertThat(image.isPlanPaused()).isTrue(); assertThat(large.isPlanPaused()).isTrue();
        assertThat(visible(s,10)).isFalse(); assertThat(visible(s,20)).isFalse();
    }
    @Test void validBonusKeepsPaidFeaturesAndExpiryAppliesFreeCut() {
        var s=store(); rule(s,10,1); var extra=rule(s,20,1);
        s.setPremiumBonusPlan(PlanType.PREMIUM_PLUS); s.setPremiumBonusExpiresAt(Instant.now().plusSeconds(3600));
        stores.saveAndFlush(s); service.reconcile(s.getStoreId());
        assertThat(extra.isPlanPaused()).isFalse(); assertThat(notices(s)).isZero();
        s.setPremiumBonusExpiresAt(Instant.now().minusSeconds(1)); stores.saveAndFlush(s); service.reconcile(s.getStoreId());
        assertThat(extra.isPlanPaused()).isTrue(); assertThat(notices(s)).isEqualTo(1);
    }
    @Test void tieBreakUsesIdAndSkipsManualDisabledOldest() {
        var s=store(); var manual=rule(s,10,1); manual.setEnabled(false); var first=rule(s,20,1); var second=rule(s,30,1);
        rules.saveAndFlush(manual);
        jdbc.update("update personalization_rules set created_at=? where store_id=?", java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), s.getStoreId());
        em.clear(); service.reconcile(s.getStoreId());
        assertThat(rules.findById(first.getId()).orElseThrow().isPlanPaused()).isFalse();
        assertThat(rules.findById(second.getId()).orElseThrow().isPlanPaused()).isTrue();
    }
    @Test void dropsRecoveredAndPreviousEpisodeNotices() {
        var s=store(); rule(s,10,1); var extra=rule(s,20,1); service.reconcile(s.getStoreId());
        var old=emails.findAll().getFirst(); assertThat(dispatcher.preview(old)).isNotNull();
        s.setPlan(PlanType.PREMIUM); service.reconcile(s.getStoreId()); assertThat(dispatcher.preview(old)).isNull();
        s.setPlan(PlanType.FREE); service.reconcile(s.getStoreId());
        assertThat(extra.isPlanPaused()).isTrue(); assertThat(dispatcher.preview(old)).isNull(); assertThat(notices(s)).isEqualTo(2);
    }
    @Test void paidAccessIsPreservedUntilEffectivePlanActuallyChanges() {
        var s=store(); s.setPlan(PlanType.PREMIUM_PLUS); rule(s,10,1); var extra=rule(s,20,2);
        stores.saveAndFlush(s); service.reconcile(s.getStoreId()); assertThat(extra.isPlanPaused()).isFalse();
        s.setBillingSuspended(true); stores.saveAndFlush(s); service.reconcile(s.getStoreId());
        assertThat(extra.isPlanPaused()).isTrue(); assertThat(visible(s,20)).isFalse();
    }
    @Test void restorationNeverExceedsPaidProductCapacity() {
        var s=store();
        for(long product=1;product<=12;product++) rule(s,product,1);
        service.reconcile(s.getStoreId());
        s.setPlan(PlanType.PREMIUM); stores.saveAndFlush(s); service.reconcile(s.getStoreId());
        var configured=rules.findWithFieldsByStoreIdOrderByCreatedAtAscIdAsc(s.getStoreId());
        assertThat(configured.stream().filter(r -> !r.isPlanPaused())).hasSize(10);
        assertThat(configured.stream().filter(PersonalizationRule::isPlanPaused).map(PersonalizationRule::getProductId))
                .containsExactly(11L,12L);
    }
    @Test void pausedImageProductDoesNotTakeRestoredProductsImageSlot() {
        var s=store(); var incompatible=rule(s,10,1); var eligible=rule(s,20,1);
        for(var rule : java.util.List.of(incompatible,eligible)) {
            var f=rule.getFields().getFirst(); f.setFieldType(FieldType.IMAGE_SELECT);
            int count=rule==incompatible ? 4 : 2;
            f.setImageOptionsJson(ImageOptions.serialize(java.util.stream.IntStream.range(0,count)
                    .mapToObj(i -> new br.com.nuvemcustomfields.dto.ImageOption(java.util.UUID.randomUUID().toString(),"Opção "+i)).toList()));
            fields.saveAndFlush(f);
        }
        service.reconcile(s.getStoreId()); s.setPlan(PlanType.PREMIUM); stores.saveAndFlush(s);
        service.reconcile(s.getStoreId());
        assertThat(incompatible.isPlanPaused()).isTrue(); assertThat(eligible.isPlanPaused()).isFalse();
        assertThat(visible(s,20)).isTrue(); assertThat(limits.imageProductVisible(s,20L)).isTrue();
        assertThat(limits.imageProductVisible(s,10L)).isFalse();
    }

    @Test void pausedProductHasPublicStatusEvenWhenNoConfigurationCanStayActive() {
        var s=store(); s.setStoreCountryCode("MX"); stores.saveAndFlush(s); var paused=rule(s,10,2);
        var reply=storefront.getFields(s.getStoreId(),10L,"/test",new MockHttpServletRequest());
        assertThat(reply.enabled()).isFalse(); assertThat(reply.paused()).isTrue();
        assertThat(reply.fields()).isEmpty(); assertThat(reply.locale()).startsWith("es");
        assertThat(storefront.getFields(s.getStoreId(),999L,"/test",new MockHttpServletRequest()).paused()).isFalse();
        paused.setEnabled(false); rules.saveAndFlush(paused);
        assertThat(storefront.getFields(s.getStoreId(),10L,"/test",new MockHttpServletRequest()).paused()).isFalse();
    }
}
