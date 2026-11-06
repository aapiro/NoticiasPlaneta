package com.noticiasplaneta.news.adapter.out.persistence;

import com.noticiasplaneta.news.application.port.in.ArticleRow;
import com.noticiasplaneta.news.application.port.out.ArticleAdminQueryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
class JdbcArticleAdminQueryAdapter implements ArticleAdminQueryRepository {

    private static final DateTimeFormatter LABEL =
            DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("es-ES"))
                    .withZone(ZoneId.of("Europe/Madrid"));

    private final NamedParameterJdbcTemplate named;

    JdbcArticleAdminQueryAdapter(JdbcTemplate jdbc) {
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArticleRow> list(String state, String sectionId, String text, int limit) {
        String sql = """
                select id, slug, section_id, title, state, origin, created_at, revision_count
                from article
                where (cast(:state as text) is null or state = cast(:state as text))
                  and (cast(:section as text) is null or section_id = cast(:section as text))
                  and (cast(:text as text) is null or title ilike '%' || cast(:text as text) || '%')
                order by created_at desc
                limit :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("state", state)
                .addValue("section", sectionId)
                .addValue("text", text)
                .addValue("limit", limit);
        return named.query(sql, params, (rs, n) -> map(rs));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsByState() {
        Map<String, Long> counts = new LinkedHashMap<>();
        named.getJdbcTemplate().query("select state, count(*) from article group by state",
                (ResultSet rs) -> {
                    counts.put(rs.getString(1), rs.getLong(2));
                });
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditRow> auditOf(String articleId) {
        String sql = "select actor_id, action, before_state, after_state, at "
                + "from audit_log where entity = 'Article' and entity_id = ? order by id asc";
        return named.getJdbcTemplate().query(sql, (rs, n) -> new AuditRow(
                rs.getString("actor_id"), rs.getString("action"),
                rs.getString("before_state"), rs.getString("after_state"),
                LABEL.format(rs.getTimestamp("at").toInstant())), articleId);
    }

    private static ArticleRow map(ResultSet rs) throws SQLException {
        Instant created = rs.getTimestamp("created_at").toInstant();
        return new ArticleRow(rs.getString("id"), rs.getString("slug"), rs.getString("section_id"),
                rs.getString("title"), rs.getString("state"), rs.getString("origin"),
                LABEL.format(created), rs.getInt("revision_count"));
    }
}
