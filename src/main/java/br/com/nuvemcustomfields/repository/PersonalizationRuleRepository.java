package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PersonalizationRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersonalizationRuleRepository extends JpaRepository<PersonalizationRule, Long> {

    List<PersonalizationRule> findByStoreIdOrderByProductNameAsc(Long storeId);

    Optional<PersonalizationRule> findByStoreIdAndProductId(Long storeId, Long productId);

    @EntityGraph(attributePaths = "fields")
    Optional<PersonalizationRule> findWithFieldsByStoreIdAndProductId(Long storeId, Long productId);

    long countByStoreId(Long storeId);

    void deleteByStoreIdAndProductId(Long storeId, Long productId);
}
