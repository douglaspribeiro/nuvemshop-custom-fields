package br.com.nuvemcustomfields.repository.jdbc;

import br.com.nuvemcustomfields.entity.FeatureFlag;
import br.com.nuvemcustomfields.repository.FeatureFlagRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcFeatureFlagRepository implements FeatureFlagRepository {

    private static final String SELECT = "SELECT flag_key, enabled, description FROM feature_flags";

    private final JdbcTemplate jdbc;

    public JdbcFeatureFlagRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public Optional<FeatureFlag> findById(String key) {
        return jdbc.query(SELECT + " WHERE flag_key = ?", JdbcEntityMapper.FEATURE_FLAG, key)
                .stream().findFirst();
    }

    @Override
    public List<FeatureFlag> findAll() {
        return jdbc.query(SELECT + " ORDER BY flag_key", JdbcEntityMapper.FEATURE_FLAG);
    }

    @Override
    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM feature_flags", Long.class);
    }

    @Override
    public FeatureFlag save(FeatureFlag flag) {
        jdbc.update("""
                INSERT INTO feature_flags (flag_key, enabled, description)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), description = VALUES(description)
                """, flag.getKey(), flag.isEnabled(), flag.getDescription());
        return flag;
    }
}
