package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.CommercePlatform;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PersonalizationRuleRepository extends JpaRepository<PersonalizationRule, Long> {

    @Query("select r from PersonalizationRule r where r.platform = br.com.nuvemcustomfields.entity.CommercePlatform.NUVEMSHOP and r.storeId = :storeId order by r.productName asc")
    List<PersonalizationRule> findByStoreIdOrderByProductNameAsc(Long storeId);

    List<PersonalizationRule> findByPlatformAndStoreIdOrderByProductNameAsc(CommercePlatform platform, Long storeId);

    @Query("select r from PersonalizationRule r where r.platform = br.com.nuvemcustomfields.entity.CommercePlatform.NUVEMSHOP and r.storeId = :storeId and r.productId = :productId")
    Optional<PersonalizationRule> findByStoreIdAndProductId(Long storeId, Long productId);

    Optional<PersonalizationRule> findByPlatformAndStoreIdAndProductId(CommercePlatform platform, Long storeId, Long productId);

    @EntityGraph(attributePaths = "fields")
    @Query("select r from PersonalizationRule r where r.platform = br.com.nuvemcustomfields.entity.CommercePlatform.NUVEMSHOP and r.storeId = :storeId and r.productId = :productId")
    Optional<PersonalizationRule> findWithFieldsByStoreIdAndProductId(Long storeId, Long productId);

    @EntityGraph(attributePaths = "fields")
    Optional<PersonalizationRule> findWithFieldsByPlatformAndStoreIdAndProductId(CommercePlatform platform, Long storeId, Long productId);

    @Query("select count(r) from PersonalizationRule r where r.platform = br.com.nuvemcustomfields.entity.CommercePlatform.NUVEMSHOP and r.storeId = :storeId")
    long countByStoreId(Long storeId);

    long countByPlatformAndStoreId(CommercePlatform platform, Long storeId);

    @Modifying
    @Query("delete from PersonalizationRule r where r.platform = br.com.nuvemcustomfields.entity.CommercePlatform.NUVEMSHOP and r.storeId = :storeId and r.productId = :productId")
    void deleteByStoreIdAndProductId(Long storeId, Long productId);

    void deleteByPlatformAndStoreIdAndProductId(CommercePlatform platform, Long storeId, Long productId);
}
