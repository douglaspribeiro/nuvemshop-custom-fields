package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PersonalizationField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonalizationFieldRepository extends JpaRepository<PersonalizationField, Long> {

    List<PersonalizationField> findByRuleIdOrderBySortOrderAscIdAsc(Long ruleId);

    long countByRuleId(Long ruleId);
}
