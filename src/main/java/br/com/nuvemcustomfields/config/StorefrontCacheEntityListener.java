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
    public StorefrontCacheEntityListener(ObjectProvider<StorefrontCache> cache) { this.cache = cache; }

    @PostPersist @PostUpdate @PostRemove
    public void changed(Object entity) {
        // Persistence-only contexts can operate without the storefront cache.
        cache.ifAvailable(StorefrontCache::invalidateAfterCommit);
    }
}
