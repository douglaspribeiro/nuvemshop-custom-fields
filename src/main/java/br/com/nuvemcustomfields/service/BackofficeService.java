package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.FeatureFlag;
import br.com.nuvemcustomfields.entity.PlanEvent;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.FeatureFlagRepository;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BackofficeService {

    private final StoreRepository storeRepository;
    private final PersonalizationRuleRepository ruleRepository;
    private final PersonalizationFieldRepository fieldRepository;
    private final PlanEventRepository planEventRepository;
    private final FeatureFlagRepository featureFlagRepository;

    public BackofficeService(
            StoreRepository storeRepository,
            PersonalizationRuleRepository ruleRepository,
            PersonalizationFieldRepository fieldRepository,
            PlanEventRepository planEventRepository,
            FeatureFlagRepository featureFlagRepository
    ) {
        this.storeRepository = storeRepository;
        this.ruleRepository = ruleRepository;
        this.fieldRepository = fieldRepository;
        this.planEventRepository = planEventRepository;
        this.featureFlagRepository = featureFlagRepository;
    }

    public long activeStores() {
        return storeRepository.findAll().stream().filter(Store::isActive).count();
    }

    public long fields() {
        return ruleRepository.findAll().stream().mapToLong(rule -> fieldRepository.countByRuleId(rule.getId())).sum();
    }

    @Transactional
    public void overridePlan(Long storeId, PlanType toPlan) {
        Store store = storeRepository.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Loja nao encontrada."));
        PlanType fromPlan = store.getEffectivePlan();
        if (store.isPremiumBonusActive()) {
            store.setPremiumBonusExpiresAt(java.time.Instant.now());
        }
        store.setPlan(toPlan);
        storeRepository.save(store);

        PlanEvent event = new PlanEvent();
        event.setStoreId(storeId);
        event.setFromPlan(fromPlan);
        event.setToPlan(toPlan);
        event.setSource("OVERRIDE");
        planEventRepository.save(event);
    }

    @Transactional
    public void updateCourtesyPremium(Long storeId, boolean courtesyPremium, String reason) {
        Store store = storeRepository.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Loja nao encontrada."));
        store.setCourtesyPremium(courtesyPremium);
        store.setCourtesyPremiumReason(normalizeReason(courtesyPremium, reason));
        storeRepository.save(store);
    }

    private String normalizeReason(boolean courtesyPremium, String reason) {
        if (!courtesyPremium || reason == null || reason.isBlank()) {
            return null;
        }
        return reason.strip();
    }

    @Transactional
    public void grantPremiumBonus(Long storeId) {
        Store store = storeRepository.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Loja nao encontrada."));
        if (!store.isActive() || store.getPlan().isBillable() || store.getSubscriptionId() != null
                || store.isCourtesyPremium()) {
            throw new IllegalArgumentException("Cortesia de 30 dias disponivel apenas para loja ativa no plano gratuito, sem assinatura ou cortesia ativa.");
        }
        java.time.Instant now = java.time.Instant.now();
        store.setPremiumBonusStartedAt(now);
        store.setPremiumBonusExpiresAt(now.plus(30, java.time.temporal.ChronoUnit.DAYS));
        storeRepository.save(store);

        PlanEvent event = new PlanEvent();
        event.setStoreId(storeId);
        event.setFromPlan(store.getPlan());
        event.setToPlan(PlanType.PREMIUM);
        event.setSource("PREMIUM_BONUS_30_DAYS");
        planEventRepository.save(event);
    }

    @Transactional
    public void saveFlag(String key, boolean enabled, String description) {
        FeatureFlag flag = featureFlagRepository.findById(key).orElseGet(FeatureFlag::new);
        flag.setKey(key.strip());
        flag.setEnabled(enabled);
        flag.setDescription(description == null || description.isBlank() ? null : description.strip());
        featureFlagRepository.save(flag);
    }
}
