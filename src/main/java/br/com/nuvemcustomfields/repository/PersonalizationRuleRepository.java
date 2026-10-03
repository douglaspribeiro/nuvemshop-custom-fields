package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PersonalizationRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PersonalizationRuleRepository extends JpaRepository<PersonalizationRule, Long> {

    List<PersonalizationRule> findByStoreIdOrderByProductNameAsc(Long storeId);

    Optional<PersonalizationRule> findByStoreIdAndProductId(Long storeId, Long productId);

    @EntityGraph(attributePaths = "fields")
    Optional<PersonalizationRule> findWithFieldsByStoreIdAndProductId(Long storeId, Long productId);

    long countByStoreId(Long storeId);

    @Query("""
            select new br.com.nuvemcustomfields.dto.PersonalizedProductMetadata(s.storeId, s.storeName, r.productId, r.productName)
            from Store s, PersonalizationRule r
            where r.storeId = s.storeId and s.uninstalledAt is null and s.erasureRequestedAt is null
              and r.enabled = true
              and exists (select f.id from PersonalizationField f where f.rule.id = r.id)
            """)
    List<br.com.nuvemcustomfields.dto.PersonalizedProductMetadata> findActiveProductMetadata();

    void deleteByStoreIdAndProductId(Long storeId, Long productId);
}
