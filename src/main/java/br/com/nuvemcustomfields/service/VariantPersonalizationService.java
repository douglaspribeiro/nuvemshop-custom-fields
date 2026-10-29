package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.HashSet;

@Service
public class VariantPersonalizationService {
    private final NuvemshopApiClient api;
    private final PersonalizationRuleRepository rules;
    private final StorefrontCache cache;

    public VariantPersonalizationService(NuvemshopApiClient api, PersonalizationRuleRepository rules, StorefrontCache cache) {
        this.api = api; this.rules = rules; this.cache = cache;
    }

    @Transactional
    public void save(Store store, Long product, boolean restricted, List<Long> selected) {
        var rule = rules.findByStoreIdAndProductId(store.getStoreId(), product)
                .orElseThrow(() -> new IllegalArgumentException("variant.product.invalid"));
        var ids = selected == null ? List.<Long>of() : selected.stream().distinct().toList();
        // Fetch through the authenticated store endpoint, even when disabling the condition.
        var available = new HashSet<>(api.listProductVariants(store, product).stream().map(NuvemshopApiClient.ProductVariant::id).toList());
        if (restricted && (ids.isEmpty() || !available.containsAll(ids))) throw new IllegalArgumentException("variant.selection.invalid");
        rule.setVariantRestricted(restricted);
        rule.setVariantIds(restricted ? ids : List.of());
        rules.save(rule);
        cache.invalidateStoreAfterCommit(store.getStoreId());
    }
}
