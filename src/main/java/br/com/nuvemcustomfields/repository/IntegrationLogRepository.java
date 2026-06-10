package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.IntegrationLog;

import java.util.List;

public interface IntegrationLogRepository {
    List<IntegrationLog> findTop20ByStoreIdOrderByCreatedAtDesc(Long storeId);
    IntegrationLog save(IntegrationLog log);
}
