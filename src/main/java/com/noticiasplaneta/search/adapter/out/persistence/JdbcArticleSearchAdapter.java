package com.noticiasplaneta.search.adapter.out.persistence;

import com.noticiasplaneta.search.application.port.in.SearchHit;
import com.noticiasplaneta.search.application.port.out.ArticleSearchRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SQL nativo a propósito: la fusión vive en el caso de uso y aquí solo se piden los dos
 * rankings. Nada de construir consultas dinámicas con concatenación de strings.
 */
@Component
class JdbcArticleSearchAdapter implements ArticleSearchRepository {

    private final JdbcTemplate jdbc;

    JdbcArticleSearchAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> lexical(String query, int limit) {
        String sql = """
                select id, slug, section_id, title,
                       ts_rank_cd(search_vector, websearch_to_tsquery('spanish', ?)) as score
                from article
                where state = 'PUBLISHED'
                  and search_vector @@ websearch_to_tsquery('spanish', ?)
                order by score desc, published_at desc
                limit ?
                """;
        return jdbc.query(sql, (rs, rowNum) -> new SearchHit(
                rs.getString("id"), rs.getString("slug"), rs.getString("section_id"),
                rs.getString("title"), rs.getDouble("score")), query, query, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> semantic(float[] embedding, int limit) {
        String sql = """
                select id, slug, section_id, title,
                       1 - (embedding <=> cast(? as vector)) as score
                from article
                where state = 'PUBLISHED' and embedding is not null
                order by embedding <=> cast(? as vector)
                limit ?
                """;
        String vector = toVectorLiteral(embedding);
        return jdbc.query(sql, (rs, rowNum) -> new SearchHit(
                rs.getString("id"), rs.getString("slug"), rs.getString("section_id"),
                rs.getString("title"), rs.getDouble("score")), vector, vector, limit);
    }

    static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }
}
