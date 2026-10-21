package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PersonalizationFieldHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PersonalizationFieldHistoryRepository extends JpaRepository<PersonalizationFieldHistory, Long> {
    List<PersonalizationFieldHistory> findByStoreId(Long storeId);
    boolean existsByStoreIdAndProductIdAndPropertyName(Long storeId, Long productId, String propertyName);
}
