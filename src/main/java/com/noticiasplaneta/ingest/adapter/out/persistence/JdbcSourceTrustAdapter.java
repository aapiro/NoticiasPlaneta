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
                select id, name, state, confianza, failure_count
                from source
                order by name
                """, (rs, n) -> new Row(
                rs.getString("id"), rs.getString("name"), rs.getString("state"),
                rs.getBoolean("confianza"), rs.getInt("failure_count")));
    }

    @Override
    @Transactional
    public boolean setTrusted(UUID sourceId, boolean trusted) {
        return jdbc.update("update source set confianza = ? where id = ?", trusted, sourceId) == 1;
    }
}
