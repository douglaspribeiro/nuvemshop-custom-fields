package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.StoreConfigurationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoreConfigurationSnapshotRepository extends JpaRepository<StoreConfigurationSnapshot, Long> {
    Optional<StoreConfigurationSnapshot> findByStoreIdAndUninstalledAt(Long storeId, Instant uninstalledAt);
    Optional<StoreConfigurationSnapshot> findByIdAndStoreId(Long id, Long storeId);
    List<StoreConfigurationSnapshot> findByStoreIdOrderByUninstalledAtDesc(Long storeId);
}
