package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.service.StorefrontCache;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

/** Hibernate obtains this listener through Spring's BeanContainer. */
@Component
public class StorefrontCacheEntityListener {
    private final ObjectProvider<StorefrontCache> cache;
    private final ObjectProvider<br.com.nuvemcustomfields.service.PlanCatalogCache> plans;
    public StorefrontCacheEntityListener(ObjectProvider<StorefrontCache> cache,
            ObjectProvider<br.com.nuvemcustomfields.service.PlanCatalogCache> plans) {
        this.cache = cache;
        this.plans = plans;
    }

    @PostPersist @PostUpdate @PostRemove
    public void changed(Object entity) {
        // Persistence-only contexts can operate without the storefront cache.
        Long storeId = null;
        if (entity instanceof br.com.nuvemcustomfields.entity.Store store) storeId = store.getStoreId();
        if (entity instanceof br.com.nuvemcustomfields.entity.PersonalizationRule rule) storeId = rule.getStoreId();
        if (entity instanceof br.com.nuvemcustomfields.entity.PersonalizationField field && field.getRule() != null)
            storeId = field.getRule().getStoreId();
        Long changedStoreId = storeId;
        if (entity instanceof br.com.nuvemcustomfields.entity.PlanAsset)
            cache.ifAvailable(StorefrontCache::invalidateAfterCommit);
        else if (changedStoreId != null)
            cache.ifAvailable(value -> value.invalidateStoreAfterCommit(changedStoreId));
        if (entity instanceof br.com.nuvemcustomfields.entity.PlanAsset)
            plans.ifAvailable(br.com.nuvemcustomfields.service.PlanCatalogCache::invalidateAfterCommit);
    }
}
