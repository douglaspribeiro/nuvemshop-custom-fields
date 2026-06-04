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
