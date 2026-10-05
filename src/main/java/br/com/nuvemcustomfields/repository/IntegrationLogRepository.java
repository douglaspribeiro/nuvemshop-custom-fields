package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.IntegrationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntegrationLogRepository extends JpaRepository<IntegrationLog, Long> {

    List<IntegrationLog> findTop20ByStoreIdOrderByCreatedAtDesc(Long storeId);
    @org.springframework.data.jpa.repository.Query("select new br.com.nuvemcustomfields.dto.StoreMetricCount(l.storeId, count(l)) "
            + "from IntegrationLog l where l.eventType in ('storefront.sdk.rendered', 'storefront.sdk.patagonia_transition_rendered') "
            + "and l.storeId is not null group by l.storeId")
    List<br.com.nuvemcustomfields.dto.StoreMetricCount> countStorefrontRenderReportsByStore();
}
