package br.com.nuvemcustomfields.repository;
import br.com.nuvemcustomfields.entity.FeatureRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.*;
public interface FeatureRequestRepository extends JpaRepository<FeatureRequest,Long> {
    Optional<FeatureRequest> findBySubmissionToken(String token);
    List<FeatureRequest> findTop50ByStoreIdOrderByCreatedAtDesc(Long storeId);
    List<FeatureRequest> findTop100ByOrderByCreatedAtDesc();
    long countByStoreIdAndCreatedAtAfter(Long storeId,Instant after);
    List<FeatureRequest> findTop50ByDiscordSentAtIsNullAndNextNotificationAtLessThanEqualOrderByCreatedAtAsc(Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select f from FeatureRequest f where f.id=:id") Optional<FeatureRequest> lockById(Long id);
}
