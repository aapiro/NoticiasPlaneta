package com.noticiasplaneta.news.adapter.out.persistence;

import com.noticiasplaneta.news.application.port.out.OutboxEvent;
import com.noticiasplaneta.news.application.port.out.OutboxPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Adaptador JDBC del outbox: un INSERT por evento y un UPDATE al procesarlo. */
@Component
class JdbcOutboxAdapter implements OutboxPort {

    private final JdbcTemplate jdbc;

    JdbcOutboxAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enqueue(OutboxEvent event) {
        String payload = "{\"url\":\"" + escape(event.url()) + "\"}";
        jdbc.update("""
                insert into outbox (id, aggregate_id, event_type, payload, created_at)
                values (?, ?, ?, cast(? as jsonb), ?)
                """,
                event.id(), event.articleId(), event.eventType(), payload,
                Timestamp.from(event.createdAt()));
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return jdbc.query("""
                select id, aggregate_id, event_type, payload->>'url' as url, created_at
                from outbox
                where processed_at is null and attempts < 5
                order by created_at
                limit ?
                """,
                (rs, rowNum) -> new OutboxEvent(
                        rs.getObject("id", UUID.class),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("url"),
                        rs.getTimestamp("created_at").toInstant()),
                limit);
    }

    @Override
    public void markProcessed(UUID id, Instant now) {
        jdbc.update("update outbox set processed_at = ?, last_error = null where id = ?",
                Timestamp.from(now), id);
    }

    @Override
    public void markFailed(UUID id, String error) {
        String safe = error == null ? "UNKNOWN" : error.substring(0, Math.min(error.length(), 500));
        jdbc.update("update outbox set attempts = attempts + 1, last_error = ? where id = ?",
                safe, id);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
