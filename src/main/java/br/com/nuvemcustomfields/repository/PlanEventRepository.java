package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PlanEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanEventRepository extends JpaRepository<PlanEvent, Long> {

    List<PlanEvent> findTop20ByStoreIdOrderByCreatedAtDesc(Long storeId);
}
