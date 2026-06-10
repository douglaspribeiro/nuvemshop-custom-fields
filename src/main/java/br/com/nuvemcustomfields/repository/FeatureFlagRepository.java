package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.FeatureFlag;

import java.util.List;
import java.util.Optional;

public interface FeatureFlagRepository {
    Optional<FeatureFlag> findById(String key);
    List<FeatureFlag> findAll();
    long count();
    FeatureFlag save(FeatureFlag flag);
}
