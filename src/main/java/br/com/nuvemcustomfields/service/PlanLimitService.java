package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.PlanUsage;
import br.com.nuvemcustomfields.entity.PersonalizationField;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class PlanLimitService {

    private static final long UNLIMITED = -1L;

    private final PersonalizationRuleRepository ruleRepository;
    private final PersonalizationFieldRepository fieldRepository;
    private final PlanCatalogService planCatalogService;

    public PlanLimitService(
            PersonalizationRuleRepository ruleRepository,
            PersonalizationFieldRepository fieldRepository,
            PlanCatalogService planCatalogService
    ) {
        this.ruleRepository = ruleRepository;
        this.fieldRepository = fieldRepository;
        this.planCatalogService = planCatalogService;
    }

    public boolean canAddProduct(Store store) {
        long limit = productLimit(store.getEffectivePlan());
        return limit == UNLIMITED || ruleRepository.countByStoreId(store.getStoreId()) < limit;
    }

    public boolean canAddField(Store store, Long ruleId) {
        long limit = fieldLimit(store.getEffectivePlan());
        return limit == UNLIMITED || fieldRepository.countByRuleId(ruleId) < limit;
    }

    public PlanUsage usage(Store store, long fieldsUsed) {
        return new PlanUsage(
                store.getEffectivePlan(),
                ruleRepository.countByStoreId(store.getStoreId()),
                productLimit(store.getEffectivePlan()),
                fieldsUsed,
                fieldLimit(store.getEffectivePlan())
        );
    }

    public List<PersonalizationField> storefrontFields(Store store, List<PersonalizationField> fields) {
        long limit = fieldLimit(store.getEffectivePlan());
        var ordered = fields.stream()
                .sorted(Comparator.comparing(PersonalizationField::getSortOrder).thenComparing(PersonalizationField::getId));
        if (limit == UNLIMITED) {
            return ordered.toList();
        }
        return ordered.limit(limit).toList();
    }

    public long productLimit(PlanType plan) {
        return planCatalogService.activePlan(plan).getProductLimit();
    }

    public long fieldLimit(PlanType plan) {
        return planCatalogService.activePlan(plan).getFieldLimit();
    }
}
