package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, String> {
}
