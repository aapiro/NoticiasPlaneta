package com.noticiasplaneta.ingest.adapter.out.persistence;

import com.noticiasplaneta.ingest.application.port.out.SourceTrustRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
class JdbcSourceTrustAdapter implements SourceTrustRepository {

    private final JdbcTemplate jdbc;

    JdbcSourceTrustAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Row> findAll() {
        return jdbc.query("""
                select id, name, feed_url, state, confianza, failure_count, last_checked_at
                from source
                order by name
                """, (rs, n) -> new Row(
                rs.getString("id"), rs.getString("name"), rs.getString("feed_url"),
                rs.getString("state"), rs.getBoolean("confianza"), rs.getInt("failure_count"),
                rs.getObject("last_checked_at", java.time.OffsetDateTime.class) == null
                        ? null
                        : rs.getObject("last_checked_at", java.time.OffsetDateTime.class).toInstant()));
    }

    @Override
    @Transactional
    public boolean setTrusted(UUID sourceId, boolean trusted) {
        return jdbc.update("update source set confianza = ? where id = ?", trusted, sourceId) == 1;
    }
}
