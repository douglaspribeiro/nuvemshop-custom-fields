package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.ConfigurationPauseEmail;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ConfigurationPauseEmailRepository extends JpaRepository<ConfigurationPauseEmail, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ConfigurationPauseEmail e where e.state = :state order by e.id")
    List<ConfigurationPauseEmail> findPending(ConfigurationPauseEmail.State state, Pageable page);
    void deleteByStoreId(Long storeId);
}
