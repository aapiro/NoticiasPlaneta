package com.noticiasplaneta.media.adapter.out.persistence;

import com.noticiasplaneta.media.application.port.in.ArticleMediaView;
import com.noticiasplaneta.media.application.port.out.ArticleMediaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Component
class JdbcArticleMediaAdapter implements ArticleMediaRepository {

    private static final String SELECT = """
            select m.id::text as media_id, m.storage_key, m.mime, m.width, m.height,
                   m.alt_text, m.credit, am.role, am.position, am.caption
            from article_media am
            join media_asset m on m.id = am.media_id
            """;

    private final NamedParameterJdbcTemplate named;

    JdbcArticleMediaAdapter(JdbcTemplate jdbc) {
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    @Transactional
    public void attach(String articleId, String mediaId, String role, int position, String caption) {
        // una sola imagen principal: si llega otra, la anterior baja al cuerpo
        if ("LEAD".equals(role)) {
            named.update("update article_media set role = 'BODY' "
                    + "where article_id = cast(:article as uuid) and role = 'LEAD'",
                    new MapSqlParameterSource("article", articleId));
        }
        named.update("""
                insert into article_media (article_id, media_id, role, position, caption)
                values (cast(:article as uuid), cast(:media as uuid), :role, :position, :caption)
                on conflict (article_id, media_id)
                do update set role = excluded.role, position = excluded.position, caption = excluded.caption
                """, new MapSqlParameterSource()
                .addValue("article", articleId)
                .addValue("media", mediaId)
                .addValue("role", role)
                .addValue("position", position)
                .addValue("caption", caption));
    }

    @Override
    @Transactional
    public void detach(String articleId, String mediaId) {
        named.update("delete from article_media "
                        + "where article_id = cast(:article as uuid) and media_id = cast(:media as uuid)",
                new MapSqlParameterSource("article", articleId).addValue("media", mediaId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArticleMediaView> of(String articleId) {
        return named.getJdbcTemplate().query(
                SELECT + "where am.article_id = cast(? as uuid) order by am.role desc, am.position",
                (rs, n) -> map(rs), articleId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArticleMediaView> recent(int limit) {
        return named.getJdbcTemplate().query(
                "select m.id::text as media_id, m.storage_key, m.mime, m.width, m.height, m.alt_text, "
                        + "m.credit, 'BODY' as role, 0 as position, null::text as caption "
                        + "from media_asset m order by m.created_at desc limit ?",
                (rs, n) -> map(rs), limit);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<String, ArticleMediaView> leadsOf(java.util.Collection<String> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return java.util.Map.of();
        }
        var params = new MapSqlParameterSource("ids", articleIds);
        java.util.Map<String, ArticleMediaView> salida = new java.util.HashMap<>();
        named.query(
                "select am.article_id::text as aid, m.id::text as media_id, m.storage_key, m.mime, "
                        + "m.width, m.height, m.alt_text, m.credit, am.role, am.position, am.caption "
                        + "from article_media am join media_asset m on m.id = am.media_id "
                        + "where am.role = 'LEAD' and am.article_id::text in (:ids)",
                params,
                rs -> {
                    salida.put(rs.getString("aid"), map(rs));
                });
        return salida;
    }

    private static ArticleMediaView map(ResultSet rs) throws SQLException {
        return new ArticleMediaView(rs.getString("media_id"), rs.getString("storage_key"),
                rs.getString("mime"), rs.getInt("width"), rs.getInt("height"),
                rs.getString("alt_text"), rs.getString("credit"), rs.getString("role"),
                rs.getInt("position"), rs.getString("caption"));
    }
}
