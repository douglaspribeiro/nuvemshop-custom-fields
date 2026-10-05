package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PersonalizationField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PersonalizationFieldRepository extends JpaRepository<PersonalizationField, Long> {

    List<PersonalizationField> findByRuleIdOrderBySortOrderAscIdAsc(Long ruleId);

    long countByRuleId(Long ruleId);

    @Query("select count(field) from PersonalizationField field where field.rule.storeId = :storeId")
    long countByStoreId(@Param("storeId") Long storeId);

    @Query("select distinct field.rule.productId from PersonalizationField field where field.rule.storeId = :storeId")
    List<Long> findConfiguredProductIdsByStoreId(@Param("storeId") Long storeId);

    @Query("select new br.com.nuvemcustomfields.dto.StoreMetricCount(f.rule.storeId, count(f)) "
            + "from PersonalizationField f group by f.rule.storeId")
    List<br.com.nuvemcustomfields.dto.StoreMetricCount> countFieldsByStore();

    @Query("select new br.com.nuvemcustomfields.dto.StoreMetricCount(f.rule.storeId, count(distinct f.rule.productId)) "
            + "from PersonalizationField f group by f.rule.storeId")
    List<br.com.nuvemcustomfields.dto.StoreMetricCount> countProductsWithFieldsByStore();

    @Query("select new br.com.nuvemcustomfields.dto.StoreMetricCount(f.rule.storeId, count(distinct f.rule.productId)) "
            + "from PersonalizationField f where f.rule.enabled = true group by f.rule.storeId")
    List<br.com.nuvemcustomfields.dto.StoreMetricCount> countEnabledProductsWithFieldsByStore();

    @Modifying
    @Query("""
            delete from PersonalizationField field
            where field.id = :fieldId
              and field.rule.id in (
                  select rule.id
                  from PersonalizationRule rule
                  where rule.storeId = :storeId
                    and rule.productId = :productId
              )
            """)
    int deleteByIdAndStoreIdAndProductId(
            @Param("fieldId") Long fieldId,
            @Param("storeId") Long storeId,
            @Param("productId") Long productId
    );
}
