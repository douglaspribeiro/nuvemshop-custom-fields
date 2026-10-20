package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.StripeCatalogPublication;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

public interface StripeCatalogPublicationRepository extends JpaRepository<StripeCatalogPublication, Long> {
    Optional<StripeCatalogPublication> findByPublicationKey(String key);
    Optional<StripeCatalogPublication> findByReservationKey(String key);
    List<StripeCatalogPublication> findTop50ByOrderByCreatedAtDesc();
    @Modifying @Transactional
    @Query("update StripeCatalogPublication p set p.leaseUntil = :until where p.id = :id and (p.leaseUntil is null or p.leaseUntil < :now)")
    int claim(Long id, Instant now, Instant until);
}
