package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.IntegrationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntegrationLogRepository extends JpaRepository<IntegrationLog, Long> {

    List<IntegrationLog> findTop20ByStoreIdOrderByCreatedAtDesc(Long storeId);
}
