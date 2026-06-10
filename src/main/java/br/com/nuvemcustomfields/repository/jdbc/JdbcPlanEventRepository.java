package br.com.nuvemcustomfields.repository.jdbc;

import br.com.nuvemcustomfields.entity.PlanEvent;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcPlanEventRepository implements PlanEventRepository {

    private static final String SELECT = """
            SELECT id, store_id, from_plan, to_plan, source, created_at
              FROM plan_events
            """;

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public JdbcPlanEventRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.insert = new SimpleJdbcInsert(dataSource)
                .withTableName("plan_events")
                .usingGeneratedKeyColumns("id");
    }

    @Override
    public List<PlanEvent> findTop20ByStoreIdOrderByCreatedAtDesc(Long storeId) {
        return jdbc.query(SELECT + " WHERE store_id = ? ORDER BY created_at DESC LIMIT 20",
                JdbcEntityMapper.PLAN_EVENT, storeId);
    }

    @Override
    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM plan_events", Long.class);
    }

    @Override
    public PlanEvent save(PlanEvent event) {
        Map<String, Object> values = values(event);
        if (event.getId() == null) {
            event.setId(insert.executeAndReturnKey(values).longValue());
            return event;
        }
        jdbc.update("""
                UPDATE plan_events
                   SET store_id = ?, from_plan = ?, to_plan = ?, source = ?, created_at = ?
                 WHERE id = ?
                """,
                values.get("store_id"), values.get("from_plan"), values.get("to_plan"),
                values.get("source"), values.get("created_at"), event.getId());
        return event;
    }

    private Map<String, Object> values(PlanEvent event) {
        Map<String, Object> values = new HashMap<>();
        values.put("store_id", event.getStoreId());
        values.put("from_plan", event.getFromPlan() == null ? null : event.getFromPlan().name());
        values.put("to_plan", event.getToPlan().name());
        values.put("source", event.getSource());
        values.put("created_at", Timestamp.from(event.getCreatedAt()));
        return values;
    }
}
