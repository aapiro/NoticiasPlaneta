package com.noticiasplaneta.ingest.adapter.out.persistence;

import com.noticiasplaneta.ingest.application.port.out.MaterialQueryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Búsqueda sobre el corpus ingerido (F8).
 *
 * <p>Primero texto completo en español ({@code websearch_to_tsquery}, que no falla con
 * comillas ni operadores sueltos); si no hay resultados, respaldo con {@code like} por
 * palabra. Ordena por relevancia y, a igualdad, por lo más reciente.
 */
@Component
class JdbcMaterialQueryAdapter implements MaterialQueryRepository {

    private final JdbcTemplate jdbc;

    JdbcMaterialQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Fila> buscar(String consulta, List<String> terminos, int limite, int ventanaDias) {
        List<Fila> porTexto = textoCompleto(consulta, limite, ventanaDias);
        if (!porTexto.isEmpty()) {
            return porTexto;
        }
        return porPalabras(terminos, limite, ventanaDias);
    }

    private List<Fila> textoCompleto(String consulta, int limite, int ventanaDias) {
        String sql = """
                select t.item_id, t.medio, t.url, t.fecha, t.titular, t.texto
                  from (select i.id::text as item_id,
                               s.name as medio,
                               i.canonical_url as url,
                               coalesce(i.published_at, i.created_at) as fecha,
                               i.title as titular,
                               left(i.body, 2000) as texto,
                               ts_rank(to_tsvector('spanish', coalesce(i.title, '') || ' ' || coalesce(i.body, '')),
                                       websearch_to_tsquery('spanish', ?)) as rank
                          from ingest_item i
                          join source s on s.id = i.source_id
                         where coalesce(i.published_at, i.created_at) >= now() - (? * interval '1 day')
                           and to_tsvector('spanish', coalesce(i.title, '') || ' ' || coalesce(i.body, ''))
                               @@ websearch_to_tsquery('spanish', ?)) t
                 order by t.rank desc, t.fecha desc
                 limit ?
                """;
        return jdbc.query(sql, (rs, n) -> fila(rs), consulta, ventanaDias, consulta, limite);
    }

    private List<Fila> porPalabras(List<String> terminos, int limite, int ventanaDias) {
        if (terminos == null || terminos.isEmpty()) {
            return List.of();
        }
        StringBuilder where = new StringBuilder();
        List<Object> parametros = new ArrayList<>();
        parametros.add(ventanaDias);
        for (String termino : terminos) {
            where.append(where.isEmpty() ? "" : " and ")
                    .append("(i.title ilike ? or i.body ilike ?)");
            parametros.add('%' + termino + '%');
            parametros.add('%' + termino + '%');
        }
        parametros.add(limite);
        String sql = """
                select i.id::text as item_id,
                       s.name as medio,
                       i.canonical_url as url,
                       coalesce(i.published_at, i.created_at) as fecha,
                       i.title as titular,
                       left(i.body, 2000) as texto
                  from ingest_item i
                  join source s on s.id = i.source_id
                 where coalesce(i.published_at, i.created_at) >= now() - (? * interval '1 day')
                   and %s
                 order by coalesce(i.published_at, i.created_at) desc
                 limit ?
                """.formatted(where);
        return jdbc.query(sql, (rs, n) -> fila(rs), parametros.toArray());
    }

    private static Fila fila(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp fecha = rs.getTimestamp("fecha");
        Instant instante = fecha == null ? null : fecha.toInstant();
        return new Fila(rs.getString("item_id"), rs.getString("medio"), rs.getString("url"),
                instante, rs.getString("titular"), rs.getString("texto"));
    }
}
