package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.CreemCatalogPublication;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

public interface CreemCatalogPublicationRepository extends JpaRepository<CreemCatalogPublication, Long> {
    Optional<CreemCatalogPublication> findByPublicationKey(String key);
    Optional<CreemCatalogPublication> findByReservationKey(String key);
    List<CreemCatalogPublication> findTop50ByOrderByCreatedAtDesc();
    @Modifying @Transactional
    @Query("update CreemCatalogPublication p set p.leaseUntil = :until where p.id = :id and (p.leaseUntil is null or p.leaseUntil < :now)")
    int claim(Long id, Instant now, Instant until);
}
