package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.CommercePlatform;
import br.com.nuvemcustomfields.entity.PersonalizationRule;

import java.util.List;
import java.util.Optional;

public interface PersonalizationRuleRepository {
    List<PersonalizationRule> findAll();
    List<PersonalizationRule> findByStoreIdOrderByProductNameAsc(Long storeId);
    List<PersonalizationRule> findByPlatformAndStoreIdOrderByProductNameAsc(CommercePlatform platform, Long storeId);
    Optional<PersonalizationRule> findByStoreIdAndProductId(Long storeId, Long productId);
    Optional<PersonalizationRule> findByPlatformAndStoreIdAndProductId(CommercePlatform platform, Long storeId, Long productId);
    Optional<PersonalizationRule> findWithFieldsByStoreIdAndProductId(Long storeId, Long productId);
    Optional<PersonalizationRule> findWithFieldsByPlatformAndStoreIdAndProductId(CommercePlatform platform, Long storeId, Long productId);
    long count();
    long countByStoreId(Long storeId);
    long countByPlatformAndStoreId(CommercePlatform platform, Long storeId);
    PersonalizationRule save(PersonalizationRule rule);
    void deleteByStoreIdAndProductId(Long storeId, Long productId);
    void deleteByPlatformAndStoreIdAndProductId(CommercePlatform platform, Long storeId, Long productId);
}
