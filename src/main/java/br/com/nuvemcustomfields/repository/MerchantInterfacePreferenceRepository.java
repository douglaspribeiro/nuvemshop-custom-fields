package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.MerchantInterfacePreference;
import br.com.nuvemcustomfields.entity.MerchantInterfacePreference.Choice;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MerchantInterfacePreferenceRepository extends JpaRepository<MerchantInterfacePreference, Long> {

    @Query("select preference.choice from MerchantInterfacePreference preference where preference.storeId = :storeId")
    Optional<Choice> findChoice(long storeId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO merchant_interface_preferences (store_id, interface_choice)
            VALUES (:storeId, :choice)
            ON DUPLICATE KEY UPDATE interface_choice = :choice
            """, nativeQuery = true)
    void setChoice(long storeId, String choice);
}
