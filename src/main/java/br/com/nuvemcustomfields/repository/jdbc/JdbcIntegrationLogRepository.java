package br.com.nuvemcustomfields.repository.jdbc;

import br.com.nuvemcustomfields.entity.IntegrationLog;
import br.com.nuvemcustomfields.repository.IntegrationLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcIntegrationLogRepository implements IntegrationLogRepository {

    private static final String SELECT = """
            SELECT id, store_id, level, event_type, message, created_at
              FROM integration_logs
            """;

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public JdbcIntegrationLogRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.insert = new SimpleJdbcInsert(dataSource)
                .withTableName("integration_logs")
                .usingGeneratedKeyColumns("id");
    }

    @Override
    public List<IntegrationLog> findTop20ByStoreIdOrderByCreatedAtDesc(Long storeId) {
        return jdbc.query(SELECT + " WHERE store_id = ? ORDER BY created_at DESC LIMIT 20",
                JdbcEntityMapper.INTEGRATION_LOG, storeId);
    }

    @Override
    public IntegrationLog save(IntegrationLog log) {
        Map<String, Object> values = values(log);
        if (log.getId() == null) {
            log.setId(insert.executeAndReturnKey(values).longValue());
            return log;
        }
        jdbc.update("""
                UPDATE integration_logs
                   SET store_id = ?, level = ?, event_type = ?, message = ?, created_at = ?
                 WHERE id = ?
                """,
                values.get("store_id"), values.get("level"), values.get("event_type"),
                values.get("message"), values.get("created_at"), log.getId());
        return log;
    }

    private Map<String, Object> values(IntegrationLog log) {
        Map<String, Object> values = new HashMap<>();
        values.put("store_id", log.getStoreId());
        values.put("level", log.getLevel());
        values.put("event_type", log.getEventType());
        values.put("message", log.getMessage());
        values.put("created_at", Timestamp.from(log.getCreatedAt()));
        return values;
    }
}
