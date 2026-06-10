package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.CommercePlatform;
import br.com.nuvemcustomfields.entity.PersonalizationField;

import java.util.List;

public interface PersonalizationFieldRepository {
    List<PersonalizationField> findByRuleIdOrderBySortOrderAscIdAsc(Long ruleId);
    long countByRuleId(Long ruleId);
    PersonalizationField save(PersonalizationField field);
    int deleteByIdAndStoreIdAndProductId(Long fieldId, Long storeId, Long productId);
    int deleteByIdAndPlatformAndStoreIdAndProductId(Long fieldId, CommercePlatform platform, Long storeId, Long productId);
}
