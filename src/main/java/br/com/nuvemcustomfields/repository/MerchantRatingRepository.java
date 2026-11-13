package br.com.nuvemcustomfields.repository;
import br.com.nuvemcustomfields.entity.MerchantRating;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface MerchantRatingRepository extends JpaRepository<MerchantRating,String> {
    boolean existsByStoreIdAndPublicReviewClickedAtIsNotNull(long storeId);
    Optional<MerchantRating> findFirstByStoreIdOrderByDisplayedAtDesc(long storeId);
    Optional<MerchantRating> findByIdAndStoreId(String id,long storeId);
}
