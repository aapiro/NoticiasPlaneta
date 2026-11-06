package com.noticiasplaneta.news.adapter.out.persistence;

import com.noticiasplaneta.news.application.port.in.PublishedArticle;
import com.noticiasplaneta.news.application.port.out.ArticleQueryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
class JdbcArticleQueryAdapter implements ArticleQueryRepository {

    private final JdbcTemplate jdbc;

    JdbcArticleQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublishedArticle> findPublished(String sectionId, int limit) {
        String sql = """
                select id, slug, section_id, title, correction_note, published_at
                from article
                where state = 'PUBLISHED'
                  and (?::text is null or section_id = ?)
                order by published_at desc nulls last
                limit ?
                """;
        return jdbc.query(sql, (rs, n) -> map(rs), sectionId, sectionId, limit);
    }

    private static final java.time.format.DateTimeFormatter LABEL =
            java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale.forLanguageTag("es-ES"))
                    .withZone(java.time.ZoneId.of("Europe/Madrid"));

    private static PublishedArticle map(java.sql.ResultSet rs) throws java.sql.SQLException {
        var ts = rs.getTimestamp("published_at");
        var instant = ts == null ? null : ts.toInstant();
        return new PublishedArticle(
                rs.getString("id"), rs.getString("slug"), rs.getString("section_id"),
                rs.getString("title"), rs.getString("correction_note"), instant,
                instant == null ? "" : LABEL.format(instant));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublishedArticle> findPublishedBySectionAndSlug(String sectionId, String slug) {
        String sql = """
                select id, slug, section_id, title, correction_note, published_at
                from article
                where state = 'PUBLISHED' and section_id = ? and slug = ?
                """;
        return jdbc.query(sql, (rs, n) -> map(rs), sectionId, slug).stream().findFirst();
    }
}
