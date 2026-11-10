package com.noticiasplaneta.news.adapter.out.persistence;

import com.noticiasplaneta.news.application.port.out.ArticleSourceRepository;
import com.noticiasplaneta.news.domain.ArticleSource;
import com.noticiasplaneta.news.domain.ContrastLevel;
import com.noticiasplaneta.news.domain.SourceKind;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JdbcArticleSourceAdapter implements ArticleSourceRepository {

    private final JdbcTemplate jdbc;

    JdbcArticleSourceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ArticleSource> findByArticleId(UUID articleId) {
        return jdbc.query("""
                select id, article_id, url, root_domain, kind, http_status, checked_at, weight, unreachable
                from article_source
                where article_id = ?
                order by created_at
                """,
                (rs, rowNum) -> new ArticleSource(
                        rs.getObject("id", UUID.class),
                        rs.getObject("article_id", UUID.class),
                        rs.getString("url"),
                        rs.getString("root_domain"),
                        SourceKind.valueOf(rs.getString("kind")),
                        rs.getInt("http_status"),
                        rs.getTimestamp("checked_at") == null ? null : rs.getTimestamp("checked_at").toInstant(),
                        rs.getInt("weight"),
                        rs.getBoolean("unreachable")),
                articleId);
    }

    @Override
    public void save(ArticleSource source) {
        jdbc.update("""
                insert into article_source
                    (id, article_id, url, root_domain, kind, http_status, checked_at, weight, unreachable)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (article_id, url) do update set
                    root_domain = excluded.root_domain,
                    kind = excluded.kind,
                    http_status = excluded.http_status,
                    checked_at = excluded.checked_at,
                    weight = excluded.weight,
                    unreachable = excluded.unreachable
                """,
                source.id(), source.articleId(), source.url(), source.rootDomain(),
                source.kind().name(), source.httpStatus(),
                source.checkedAt() == null ? null : Timestamp.from(source.checkedAt()),
                source.weight(), source.unreachable());
    }

    @Override
    public void updateContrast(UUID articleId, int independentSourceCount, ContrastLevel level) {
        jdbc.update("""
                update article
                set independent_source_count = ?, contrast_level = ?
                where id = ?
                """,
                independentSourceCount, level.name(), articleId);
    }

    @Override
    public Optional<ContrastSnapshot> contrast(UUID articleId) {
        return jdbc.query("""
                select independent_source_count, contrast_level
                from article
                where id = ?
                """,
                (rs, rowNum) -> new ContrastSnapshot(
                        rs.getInt("independent_source_count"),
                        rs.getString("contrast_level")),
                articleId).stream().findFirst();
    }
}
