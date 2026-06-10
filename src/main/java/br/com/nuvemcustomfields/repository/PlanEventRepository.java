package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PlanEvent;

import java.util.List;

public interface PlanEventRepository {
    List<PlanEvent> findTop20ByStoreIdOrderByCreatedAtDesc(Long storeId);
    long count();
    PlanEvent save(PlanEvent event);
}
