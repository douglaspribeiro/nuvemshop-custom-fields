package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.StoreTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StoreTemplateRepository extends JpaRepository<StoreTemplate,Long> {
    long countByStoreId(Long storeId);
    List<StoreTemplate> findByStoreIdOrderByUpdatedAtDescIdDesc(Long storeId);
    Optional<StoreTemplate> findByIdAndStoreId(Long id,Long storeId);
}
