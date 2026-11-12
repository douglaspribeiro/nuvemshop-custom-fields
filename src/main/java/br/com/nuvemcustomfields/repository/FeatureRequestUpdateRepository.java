package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.FeatureRequestUpdate;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface FeatureRequestUpdateRepository extends JpaRepository<FeatureRequestUpdate,Long> {
    Optional<FeatureRequestUpdate> findBySubmissionToken(String token);
    List<FeatureRequestUpdate> findTop50ByEmailStateOrderByCreatedAtAsc(String state);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from FeatureRequestUpdate u where u.id=:id")
    Optional<FeatureRequestUpdate> lockById(Long id);
}
