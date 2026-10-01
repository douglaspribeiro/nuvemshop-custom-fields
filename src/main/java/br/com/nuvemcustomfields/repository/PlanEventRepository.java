package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PlanEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanEventRepository extends JpaRepository<PlanEvent, Long> {

    List<PlanEvent> findTop20ByStoreIdOrderByCreatedAtDesc(Long storeId);
    boolean existsByStoreIdAndToPlanInAndSourceIn(Long storeId,
            java.util.Collection<br.com.nuvemcustomfields.entity.PlanType> plans, java.util.Collection<String> sources);
}
