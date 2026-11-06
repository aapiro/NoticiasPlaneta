package com.noticiasplaneta.news.adapter.out.persistence;

import com.noticiasplaneta.news.application.port.out.ArticleRevisionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
class JdbcArticleRevisionAdapter implements ArticleRevisionRepository {

    private static final DateTimeFormatter LABEL =
            DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("es-ES"))
                    .withZone(ZoneId.of("Europe/Madrid"));

    private final NamedParameterJdbcTemplate named;

    JdbcArticleRevisionAdapter(JdbcTemplate jdbc) {
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public void save(String articleId, Snapshot s) {
        named.update("""
                insert into article_revision (article_id, revision, title, body, edited_by, edited_at, note)
                values (cast(:id as uuid), :rev, :title, :body, :by, :at, :note)
                on conflict (article_id, revision) do nothing
                """, new MapSqlParameterSource()
                .addValue("id", articleId)
                .addValue("rev", s.revision())
                .addValue("title", s.title())
                .addValue("body", s.body())
                .addValue("by", s.editedBy())
                .addValue("at", java.sql.Timestamp.from(Instant.now()))
                .addValue("note", s.note()));
    }

    @Override
    public List<Snapshot> byArticle(String articleId) {
        return named.getJdbcTemplate().query("""
                select revision, title, body, edited_by, edited_at, note
                from article_revision where article_id = cast(? as uuid)
                order by revision desc
                """, (rs, n) -> new Snapshot(rs.getInt("revision"), rs.getString("title"),
                rs.getString("body"), rs.getString("edited_by"),
                LABEL.format(rs.getTimestamp("edited_at").toInstant()), rs.getString("note")),
                articleId);
    }

    @Override
    public Optional<Snapshot> byArticleAndRevision(String articleId, int revision) {
        return byArticle(articleId).stream().filter(s -> s.revision() == revision).findFirst();
    }
}
