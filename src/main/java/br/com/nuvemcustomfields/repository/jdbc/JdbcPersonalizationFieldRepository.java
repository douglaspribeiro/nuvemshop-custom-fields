package br.com.nuvemcustomfields.repository.jdbc;

import br.com.nuvemcustomfields.entity.CommercePlatform;
import br.com.nuvemcustomfields.entity.PersonalizationField;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcPersonalizationFieldRepository implements PersonalizationFieldRepository {

    private static final String SELECT = """
            SELECT id, rule_id, label, field_type, required, max_length, placeholder,
                   validation_pattern, options_text, sort_order
              FROM personalization_fields
            """;

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public JdbcPersonalizationFieldRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.insert = new SimpleJdbcInsert(dataSource)
                .withTableName("personalization_fields")
                .usingGeneratedKeyColumns("id");
    }

    @Override
    public List<PersonalizationField> findByRuleIdOrderBySortOrderAscIdAsc(Long ruleId) {
        PersonalizationRule rule = new PersonalizationRule();
        rule.setId(ruleId);
        List<PersonalizationField> fields = jdbc.query(
                SELECT + " WHERE rule_id = ? ORDER BY sort_order, id", JdbcEntityMapper.FIELD, ruleId);
        fields.forEach(field -> field.setRule(rule));
        return fields;
    }

    @Override
    public long countByRuleId(Long ruleId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM personalization_fields WHERE rule_id = ?", Long.class, ruleId);
    }

    @Override
    public PersonalizationField save(PersonalizationField field) {
        if (field.getRule() == null || field.getRule().getId() == null) {
            throw new IllegalArgumentException("O campo precisa estar associado a uma regra persistida");
        }
        Map<String, Object> values = values(field);
        if (field.getId() == null) {
            field.setId(insert.executeAndReturnKey(values).longValue());
            return field;
        }
        jdbc.update("""
                UPDATE personalization_fields
                   SET rule_id = ?, label = ?, field_type = ?, required = ?, max_length = ?,
                       placeholder = ?, validation_pattern = ?, options_text = ?, sort_order = ?
                 WHERE id = ?
                """,
                values.get("rule_id"), values.get("label"), values.get("field_type"), values.get("required"),
                values.get("max_length"), values.get("placeholder"), values.get("validation_pattern"),
                values.get("options_text"), values.get("sort_order"), field.getId());
        return field;
    }

    @Override
    public int deleteByIdAndStoreIdAndProductId(Long fieldId, Long storeId, Long productId) {
        return deleteByIdAndPlatformAndStoreIdAndProductId(
                fieldId, CommercePlatform.NUVEMSHOP, storeId, productId);
    }

    @Override
    public int deleteByIdAndPlatformAndStoreIdAndProductId(
            Long fieldId, CommercePlatform platform, Long storeId, Long productId) {
        return jdbc.update("""
                DELETE FROM personalization_fields
                 WHERE id = ?
                   AND rule_id IN (
                       SELECT id
                         FROM personalization_rules
                        WHERE platform = ? AND store_id = ? AND product_id = ?
                   )
                """, fieldId, platform.name(), storeId, productId);
    }

    private Map<String, Object> values(PersonalizationField field) {
        Map<String, Object> values = new HashMap<>();
        values.put("rule_id", field.getRule().getId());
        values.put("label", field.getLabel());
        values.put("field_type", field.getFieldType().name());
        values.put("required", field.isRequired());
        values.put("max_length", field.getMaxLength());
        values.put("placeholder", field.getPlaceholder());
        values.put("validation_pattern", field.getValidationPattern());
        values.put("options_text", field.getOptionsText());
        values.put("sort_order", field.getSortOrder());
        return values;
    }
}
