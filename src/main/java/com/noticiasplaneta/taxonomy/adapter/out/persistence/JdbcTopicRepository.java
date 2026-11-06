package com.noticiasplaneta.taxonomy.adapter.out.persistence;

import com.noticiasplaneta.taxonomy.application.port.in.TopicTimeline;
import com.noticiasplaneta.taxonomy.application.port.out.TopicRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
class JdbcTopicRepository implements TopicRepository {

    private static final DateTimeFormatter LABEL =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("es-ES"))
                    .withZone(ZoneId.of("Europe/Madrid"));

    private final JdbcTemplate jdbc;

    JdbcTopicRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TopicTimeline> findTimeline(String slug) {
        String sql = """
                select id, slug, title, summary from topic where slug = ?
                """;
        return jdbc.query(sql, (rs, n) -> rs.getString("id"), slug).stream().findFirst()
                .map(id -> {
                    List<TopicTimeline.Entry> entries = entries(slug, null);
                    return new TopicTimeline(slug,
                            jdbc.queryForObject("select title from topic where slug = ?", String.class, slug),
                            jdbc.query("select summary from topic where slug = ?",
                                    (rs, n) -> rs.getString("summary"), slug).stream().findFirst().orElse(null),
                            entries, null, List.of());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicTimeline.Entry> findPublishedAfter(String slug, Instant since) {
        return entries(slug, since);
    }

    private List<TopicTimeline.Entry> entries(String slug, Instant since) {
        String sql = """
                select a.id, a.slug, a.section_id, a.title, a.published_at
                from article a
                join article_topic at on at.article_id = a.id
                join topic t on t.id = at.topic_id
                where t.slug = :slug
                  and a.state = 'PUBLISHED'
                  and (cast(:since as timestamptz) is null
                       or a.published_at > cast(:since as timestamptz))
                order by a.published_at desc nulls last
                limit 50
                """;
        var params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("slug", slug)
                .addValue("since", since == null ? null : java.sql.Timestamp.from(since));
        return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc)
                .query(sql, params, (rs, n) -> map(rs));
    }

    private static TopicTimeline.Entry map(ResultSet rs) throws SQLException {
        var ts = rs.getTimestamp("published_at");
        Instant instant = ts == null ? null : ts.toInstant();
        return new TopicTimeline.Entry(rs.getString("id"), rs.getString("slug"),
                rs.getString("section_id"), rs.getString("title"), instant,
                instant == null ? "" : LABEL.format(instant));
    }
}
