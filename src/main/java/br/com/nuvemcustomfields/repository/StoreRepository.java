package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByStoreId(Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Store s where s.storeId = :storeId")
    Optional<Store> findByStoreIdForUpdate(Long storeId);

    List<Store> findByStoreIdIn(Collection<Long> storeIds);

    boolean existsByStoreIdAndUninstalledAtIsNull(Long storeId);

    @Query("select s from Store s where s.storeId = :storeId and s.uninstalledAt is null and s.erasureRequestedAt is null")
    Optional<Store> findActiveByStoreId(Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Store s where s.storeId = :storeId and s.uninstalledAt is null and s.erasureRequestedAt is null")
    Optional<Store> findActiveByStoreIdForUpdate(Long storeId);
    @Modifying
    @Transactional
    @Query(value = "update stores set last_admin_access_at = :accessAt where store_id = :storeId "
            + "and uninstalled_at is null and erasure_requested_at is null "
            + "and (last_admin_access_at is null or last_admin_access_at <= :cutoff)", nativeQuery = true)
    int recordAdminAccess(@Param("storeId") Long storeId, @Param("accessAt") Instant accessAt, @Param("cutoff") Instant cutoff);

}
