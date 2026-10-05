package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PlanAsset;
import br.com.nuvemcustomfields.entity.FieldType;
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

    private final PlanCatalogService catalog;
    private final PersonalizationRuleRepository ruleRepository;
    private final PersonalizationFieldRepository fieldRepository;

    public PlanLimitService(PersonalizationRuleRepository ruleRepository, PersonalizationFieldRepository fieldRepository, PlanCatalogService catalog) {
        this.catalog = catalog;
        this.ruleRepository = ruleRepository;
        this.fieldRepository = fieldRepository;
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

    public long imageProductLimit(PlanType plan) { return catalog.activePlan(plan).getImageProductLimit(); }
    public int imageOptionLimit(PlanType plan) { return catalog.activePlan(plan).getImageOptionLimit(); }
    public List<Long> imageProductIds(Long storeId) { return fieldRepository.findImageProductIdsByStoreId(storeId); }
    public boolean canConfigureImageProduct(Store store, Long productId) {
        long limit = imageProductLimit(store.getEffectivePlan());
        if (limit == 0) return false;
        if (limit == UNLIMITED) return true;
        var products = imageProductIds(store.getStoreId());
        int index = products.indexOf(productId);
        return index >= 0 ? index < limit : products.size() < limit;
    }
    public boolean imageProductVisible(Store store, Long productId) {
        long limit = imageProductLimit(store.getEffectivePlan());
        if (limit == UNLIMITED) return true;
        if (limit == 0) return false;
        var products = imageProductIds(store.getStoreId());
        int index = products.indexOf(productId);
        return index >= 0 && index < limit;
    }
    public void requireImageConfiguration(Store store, Long productId, int options) {
        if (imageProductLimit(store.getEffectivePlan()) == 0)
            throw new ImagePlanLimitException("image.plan.unavailable");
        if (!canConfigureImageProduct(store, productId))
            throw new ImagePlanLimitException("image.plan.products.limit", imageProductLimit(store.getEffectivePlan()));
        if (options > imageOptionLimit(store.getEffectivePlan()))
            throw new ImagePlanLimitException("image.plan.options.limit", imageOptionLimit(store.getEffectivePlan()));
    }

    public List<PersonalizationField> storefrontFields(Store store, List<PersonalizationField> fields) {
        long limit = fieldLimit(store.getEffectivePlan());
        boolean hasImages = fields.stream().anyMatch(field -> field.getFieldType() == FieldType.IMAGE_SELECT);
        long imageLimit = hasImages ? imageProductLimit(store.getEffectivePlan()) : 0;
        var visibleProducts = imageLimit > 0
                ? imageProductIds(store.getStoreId()).stream().limit(imageLimit).collect(java.util.stream.Collectors.toSet())
                : java.util.Set.<Long>of();
        var ordered = fields.stream()
                .filter(field -> field.getFieldType() != FieldType.IMAGE_SELECT
                        || imageLimit == UNLIMITED || visibleProducts.contains(field.getRule().getProductId()))
                .sorted(Comparator.comparing(PersonalizationField::getSortOrder).thenComparing(PersonalizationField::getId));
        if (limit == UNLIMITED) {
            return ordered.toList();
        }
        return ordered.limit(limit).toList();
    }

    public java.util.Map<String, PlanAsset> planDefinitions() {
        return catalog.activePlansByType().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                entry -> entry.getKey().name(), java.util.Map.Entry::getValue));
    }

    public long productLimit(PlanType plan) {
        return catalog.activePlan(plan).getProductLimit();
    }

    public long fieldLimit(PlanType plan) {
        return catalog.activePlan(plan).getFieldLimit();
    }
}
