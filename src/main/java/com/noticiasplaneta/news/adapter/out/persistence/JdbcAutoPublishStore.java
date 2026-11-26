package com.noticiasplaneta.news.adapter.out.persistence;

import com.noticiasplaneta.news.application.port.in.AutoPublishUseCase;
import com.noticiasplaneta.news.application.port.out.AutoPublishStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consultas y marcas de la publicación automática (F6).
 *
 * <p>Todas las reglas de selección están en el {@code where}: ventana de 48 h sobre la
 * fecha de la noticia, fuente marcada de confianza y no deshabilitada, sección activa,
 * sin clasificación ni publicación previa, y anti-repetición por titular normalizado.
 */
@Component
class JdbcAutoPublishStore implements AutoPublishStore {

    /** Sin cuerpo no hay pieza: un titular solo no se publica solo. */
    private static final int MIN_BODY_CHARS = 80;

    private final NamedParameterJdbcTemplate named;

    JdbcAutoPublishStore(NamedParameterJdbcTemplate named) {
        this.named = named;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AutoPublishUseCase.Candidate> candidatesForClassification(Instant from, Instant to, int limit) {
        String sql = """
                select a.id, a.title, a.section_id, s.name as source_name,
                       coalesce(i.published_at, i.created_at) as fecha,
                       null::text as modelo, null::text as prompt_version
                from article a
                join ingest_item i on i.id::text = a.ingest_item_id
                join source s on s.id = i.source_id
                join section_config sc on sc.slug = a.section_id
                where a.state = 'DRAFT'
                  and a.origin = 'INGEST'
                  and a.ingest_item_id is not null
                  and length(btrim(a.body)) >= :minBody
                  and s.confianza
                  and s.state <> 'DISABLED'
                  and sc.activa
                  and coalesce(i.published_at, i.created_at) > :desde
                  and coalesce(i.published_at, i.created_at) <= :ahora
                  and not exists (select 1 from article_auto_classification ac where ac.article_id = a.id)
                  and not exists (select 1 from article_auto_publicacion ap where ap.article_id = a.id)
                order by fecha desc
                limit :limite
                """;
        var params = new MapSqlParameterSource()
                .addValue("desde", java.sql.Timestamp.from(from))
                .addValue("ahora", java.sql.Timestamp.from(to))
                .addValue("minBody", MIN_BODY_CHARS)
                .addValue("limite", limit);
        return named.query(sql, params, JdbcAutoPublishStore::map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AutoPublishUseCase.Candidate> candidatesForPublication(String sectionId, Instant from, Instant to,
                                                                       Instant publishedSince, int limit) {
        String sql = """
                select a.id, a.title, a.section_id, s.name as source_name,
                       coalesce(i.published_at, i.created_at) as fecha,
                       ac.modelo, ac.prompt_version
                from article a
                join ingest_item i on i.id::text = a.ingest_item_id
                join source s on s.id = i.source_id
                join section_config sc on sc.slug = a.section_id
                join article_auto_classification ac on ac.article_id = a.id
                where a.state = 'DRAFT'
                  and a.origin = 'INGEST'
                  and a.section_id = :seccion
                  and a.ingest_item_id is not null
                  and length(btrim(a.body)) >= :minBody
                  and s.confianza
                  and s.state <> 'DISABLED'
                  and sc.activa
                  and sc.auto_publica
                  and coalesce(i.published_at, i.created_at) > :desde
                  and coalesce(i.published_at, i.created_at) <= :ahora
                  and not exists (select 1 from article_auto_publicacion ap where ap.article_id = a.id)
                  and not exists (
                      select 1 from article p
                      where p.state = 'PUBLISHED'
                        and p.id <> a.id
                        and p.published_at >= :publicadoDesde
                        and lower(regexp_replace(p.title, '[^a-z0-9áéíóúüñ]+', ' ', 'g'))
                          = lower(regexp_replace(a.title, '[^a-z0-9áéíóúüñ]+', ' ', 'g'))
                  )
                order by fecha desc
                limit :limite
                """;
        var params = new MapSqlParameterSource()
                .addValue("seccion", sectionId)
                .addValue("desde", java.sql.Timestamp.from(from))
                .addValue("ahora", java.sql.Timestamp.from(to))
                .addValue("publicadoDesde", java.sql.Timestamp.from(publishedSince))
                .addValue("minBody", MIN_BODY_CHARS)
                .addValue("limite", limit);
        return named.query(sql, params, JdbcAutoPublishStore::map);
    }

    @Override
    @Transactional
    public void saveClassification(UUID articleId, String sectionId, String model, String promptVersion,
                                   double confidence, Instant at) {
        String sql = """
                insert into article_auto_classification
                    (article_id, seccion, modelo, prompt_version, confianza, clasificada_en)
                values (:id, :seccion, :modelo, :prompt, :confianza, :at)
                on conflict (article_id) do update
                    set seccion = excluded.seccion,
                        modelo = excluded.modelo,
                        prompt_version = excluded.prompt_version,
                        confianza = excluded.confianza,
                        clasificada_en = excluded.clasificada_en
                """;
        named.update(sql, new MapSqlParameterSource()
                .addValue("id", articleId)
                .addValue("seccion", sectionId)
                .addValue("modelo", model)
                .addValue("prompt", promptVersion)
                .addValue("confianza", confidence)
                .addValue("at", java.sql.Timestamp.from(at)));
    }

    @Override
    @Transactional
    public boolean markAutoPublished(UUID articleId, String sectionId, String bias, String model,
                                     String promptVersion, List<String> rules, Instant at) {
        int inserted = named.update("""
                insert into article_auto_publicacion
                    (article_id, seccion, enfoque, modelo, prompt_version, reglas, publicado_en)
                values (:id, :seccion, :enfoque, :modelo, :prompt, cast(:reglas as text[]), :at)
                on conflict (article_id) do nothing
                """, new MapSqlParameterSource()
                .addValue("id", articleId)
                .addValue("seccion", sectionId)
                .addValue("enfoque", bias)
                .addValue("modelo", model)
                .addValue("prompt", promptVersion)
                .addValue("reglas", toArray(rules))
                .addValue("at", java.sql.Timestamp.from(at)));
        if (inserted == 0) {
            return false;
        }
        // El enfoque con el que se escribió la pieza (ADR-012). Si un editor ya lo fijó, manda el suyo.
        named.update("update article set editorial_bias = coalesce(editorial_bias, :enfoque) where id = :id",
                new MapSqlParameterSource().addValue("id", articleId).addValue("enfoque", bias));
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public long countAutoPublished(String sectionId, Instant from, Instant to) {
        Long count = named.queryForObject("""
                select count(*) from article_auto_publicacion
                where seccion = :seccion and publicado_en >= :desde and publicado_en < :hasta
                """, new MapSqlParameterSource()
                .addValue("seccion", sectionId)
                .addValue("desde", java.sql.Timestamp.from(from))
                .addValue("hasta", java.sql.Timestamp.from(to)), Long.class);
        return count == null ? 0 : count;
    }

    private static String toArray(List<String> rules) {
        if (rules == null || rules.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < rules.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(rules.get(i).replace("\"", "\\\"")).append('"');
        }
        return sb.append('}').toString();
    }

    private static AutoPublishUseCase.Candidate map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        var ts = rs.getTimestamp("fecha");
        return new AutoPublishUseCase.Candidate(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("section_id"),
                rs.getString("source_name"),
                ts == null ? null : ts.toInstant(),
                rs.getString("modelo"),
                rs.getString("prompt_version"));
    }
}
