package br.com.nuvemcustomfields.repository.jdbc;

import br.com.nuvemcustomfields.entity.CommercePlatform;
import br.com.nuvemcustomfields.entity.PersonalizationField;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcPersonalizationRuleRepository implements PersonalizationRuleRepository {

    private static final String SELECT = """
            SELECT id, platform, store_id, product_id, product_name, enabled, created_at
              FROM personalization_rules
            """;
    private static final String SELECT_FIELDS = """
            SELECT id, rule_id, label, field_type, required, max_length, placeholder,
                   validation_pattern, options_text, sort_order
              FROM personalization_fields
             WHERE rule_id = ?
             ORDER BY sort_order, id
            """;

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public JdbcPersonalizationRuleRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.insert = new SimpleJdbcInsert(dataSource)
                .withTableName("personalization_rules")
                .usingGeneratedKeyColumns("id");
    }

    @Override
    public List<PersonalizationRule> findAll() {
        return jdbc.query(SELECT + " ORDER BY id", JdbcEntityMapper.RULE);
    }

    @Override
    public List<PersonalizationRule> findByStoreIdOrderByProductNameAsc(Long storeId) {
        return findByPlatformAndStoreIdOrderByProductNameAsc(CommercePlatform.NUVEMSHOP, storeId);
    }

    @Override
    public List<PersonalizationRule> findByPlatformAndStoreIdOrderByProductNameAsc(
            CommercePlatform platform, Long storeId) {
        return jdbc.query(SELECT + " WHERE platform = ? AND store_id = ? ORDER BY product_name",
                JdbcEntityMapper.RULE, platform.name(), storeId);
    }

    @Override
    public Optional<PersonalizationRule> findByStoreIdAndProductId(Long storeId, Long productId) {
        return findByPlatformAndStoreIdAndProductId(CommercePlatform.NUVEMSHOP, storeId, productId);
    }

    @Override
    public Optional<PersonalizationRule> findByPlatformAndStoreIdAndProductId(
            CommercePlatform platform, Long storeId, Long productId) {
        return jdbc.query(SELECT + " WHERE platform = ? AND store_id = ? AND product_id = ?",
                JdbcEntityMapper.RULE, platform.name(), storeId, productId).stream().findFirst();
    }

    @Override
    public Optional<PersonalizationRule> findWithFieldsByStoreIdAndProductId(Long storeId, Long productId) {
        return findWithFieldsByPlatformAndStoreIdAndProductId(CommercePlatform.NUVEMSHOP, storeId, productId);
    }

    @Override
    public Optional<PersonalizationRule> findWithFieldsByPlatformAndStoreIdAndProductId(
            CommercePlatform platform, Long storeId, Long productId) {
        return findByPlatformAndStoreIdAndProductId(platform, storeId, productId).map(this::loadFields);
    }

    @Override
    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM personalization_rules", Long.class);
    }

    @Override
    public long countByStoreId(Long storeId) {
        return countByPlatformAndStoreId(CommercePlatform.NUVEMSHOP, storeId);
    }

    @Override
    public long countByPlatformAndStoreId(CommercePlatform platform, Long storeId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM personalization_rules WHERE platform = ? AND store_id = ?",
                Long.class, platform.name(), storeId);
    }

    @Override
    public PersonalizationRule save(PersonalizationRule rule) {
        Map<String, Object> values = values(rule);
        if (rule.getId() == null) {
            rule.setId(insert.executeAndReturnKey(values).longValue());
            return rule;
        }
        jdbc.update("""
                UPDATE personalization_rules
                   SET platform = ?, store_id = ?, product_id = ?, product_name = ?,
                       enabled = ?, created_at = ?
                 WHERE id = ?
                """,
                values.get("platform"), values.get("store_id"), values.get("product_id"),
                values.get("product_name"), values.get("enabled"), values.get("created_at"), rule.getId());
        return rule;
    }

    @Override
    public void deleteByStoreIdAndProductId(Long storeId, Long productId) {
        deleteByPlatformAndStoreIdAndProductId(CommercePlatform.NUVEMSHOP, storeId, productId);
    }

    @Override
    public void deleteByPlatformAndStoreIdAndProductId(
            CommercePlatform platform, Long storeId, Long productId) {
        jdbc.update("DELETE FROM personalization_rules WHERE platform = ? AND store_id = ? AND product_id = ?",
                platform.name(), storeId, productId);
    }

    private PersonalizationRule loadFields(PersonalizationRule rule) {
        List<PersonalizationField> fields = jdbc.query(SELECT_FIELDS, JdbcEntityMapper.FIELD, rule.getId());
        fields.forEach(field -> field.setRule(rule));
        rule.getFields().addAll(fields);
        return rule;
    }

    private Map<String, Object> values(PersonalizationRule rule) {
        Map<String, Object> values = new HashMap<>();
        values.put("platform", rule.getPlatform().name());
        values.put("store_id", rule.getStoreId());
        values.put("product_id", rule.getProductId());
        values.put("product_name", rule.getProductName());
        values.put("enabled", rule.isEnabled());
        values.put("created_at", Timestamp.from(rule.getCreatedAt()));
        return values;
    }
}
