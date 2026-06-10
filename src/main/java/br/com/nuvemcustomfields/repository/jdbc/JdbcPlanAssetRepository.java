package br.com.nuvemcustomfields.repository.jdbc;

import br.com.nuvemcustomfields.entity.PlanAsset;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.repository.PlanAssetRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcPlanAssetRepository implements PlanAssetRepository {

    private static final String SELECT = """
            SELECT id, plan_type, display_name, description, billing_external_id, currency,
                   amount, product_limit, field_limit, effective_from, effective_until,
                   active, created_at
              FROM plan_assets
            """;

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public JdbcPlanAssetRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.insert = new SimpleJdbcInsert(dataSource)
                .withTableName("plan_assets")
                .usingGeneratedKeyColumns("id");
    }

    @Override
    public List<PlanAsset> findByActiveTrueOrderByPlanTypeAscEffectiveFromDesc() {
        return jdbc.query(SELECT + " WHERE active = TRUE ORDER BY plan_type, effective_from DESC",
                JdbcEntityMapper.PLAN_ASSET);
    }

    @Override
    public List<PlanAsset> findByBillingExternalIdAndActiveTrueOrderByEffectiveFromDesc(String billingExternalId) {
        return jdbc.query(SELECT + """
                 WHERE billing_external_id = ? AND active = TRUE
                 ORDER BY effective_from DESC
                """, JdbcEntityMapper.PLAN_ASSET, billingExternalId);
    }

    @Override
    public List<PlanAsset> findActiveByPlanTypeOnDate(PlanType planType, LocalDate date) {
        return jdbc.query(SELECT + """
                 WHERE plan_type = ? AND active = TRUE
                   AND effective_from <= ?
                   AND (effective_until IS NULL OR effective_until >= ?)
                 ORDER BY effective_from DESC
                """, JdbcEntityMapper.PLAN_ASSET, planType.name(), Date.valueOf(date), Date.valueOf(date));
    }

    @Override
    public List<PlanAsset> findActiveByBillingExternalIdOnDate(String externalId, LocalDate date) {
        return jdbc.query(SELECT + """
                 WHERE billing_external_id = ? AND active = TRUE
                   AND effective_from <= ?
                   AND (effective_until IS NULL OR effective_until >= ?)
                 ORDER BY effective_from DESC
                """, JdbcEntityMapper.PLAN_ASSET, externalId, Date.valueOf(date), Date.valueOf(date));
    }

    @Override
    public List<PlanAsset> findOverlappingActiveVersions(
            PlanType planType, LocalDate effectiveFrom, LocalDate effectiveUntil) {
        Date until = effectiveUntil == null ? null : Date.valueOf(effectiveUntil);
        return jdbc.query(SELECT + """
                 WHERE plan_type = ? AND active = TRUE
                   AND (effective_until IS NULL OR effective_until >= ?)
                   AND (? IS NULL OR effective_from <= ?)
                 ORDER BY effective_from DESC
                """, JdbcEntityMapper.PLAN_ASSET, planType.name(), Date.valueOf(effectiveFrom), until, until);
    }

    @Override
    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM plan_assets", Long.class);
    }

    @Override
    public PlanAsset save(PlanAsset asset) {
        Map<String, Object> values = values(asset);
        if (asset.getId() == null) {
            asset.setId(insert.executeAndReturnKey(values).longValue());
            return asset;
        }
        jdbc.update("""
                UPDATE plan_assets
                   SET plan_type = ?, display_name = ?, description = ?, billing_external_id = ?,
                       currency = ?, amount = ?, product_limit = ?, field_limit = ?,
                       effective_from = ?, effective_until = ?, active = ?, created_at = ?
                 WHERE id = ?
                """,
                values.get("plan_type"), values.get("display_name"), values.get("description"),
                values.get("billing_external_id"), values.get("currency"), values.get("amount"),
                values.get("product_limit"), values.get("field_limit"), values.get("effective_from"),
                values.get("effective_until"), values.get("active"), values.get("created_at"), asset.getId());
        return asset;
    }

    private Map<String, Object> values(PlanAsset asset) {
        Map<String, Object> values = new HashMap<>();
        values.put("plan_type", asset.getPlanType().name());
        values.put("display_name", asset.getDisplayName());
        values.put("description", asset.getDescription());
        values.put("billing_external_id", asset.getBillingExternalId());
        values.put("currency", asset.getCurrency());
        values.put("amount", asset.getAmount());
        values.put("product_limit", asset.getProductLimit());
        values.put("field_limit", asset.getFieldLimit());
        values.put("effective_from", Date.valueOf(asset.getEffectiveFrom()));
        values.put("effective_until",
                asset.getEffectiveUntil() == null ? null : Date.valueOf(asset.getEffectiveUntil()));
        values.put("active", asset.isActive());
        values.put("created_at", Timestamp.from(asset.getCreatedAt()));
        return values;
    }
}
