package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByStoreId(Long storeId);

    @Query("select s from Store s where s.storeId = :storeId and s.uninstalledAt is null")
    Optional<Store> findActiveByStoreId(Long storeId);
}
