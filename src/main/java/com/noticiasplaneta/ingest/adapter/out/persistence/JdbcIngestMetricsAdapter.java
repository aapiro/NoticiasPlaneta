package com.noticiasplaneta.ingest.adapter.out.persistence;

import com.noticiasplaneta.ingest.application.port.out.IngestMetricsRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class JdbcIngestMetricsAdapter implements IngestMetricsRepository {

    private final JdbcTemplate jdbc;

    JdbcIngestMetricsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Totales totales() {
        return jdbc.queryForObject("""
                select
                  (select count(*) from article)                                    as articulos,
                  (select count(*) from article where state = 'PUBLISHED')          as publicados,
                  (select count(*) from article where state = 'DRAFT')              as borradores,
                  (select count(*) from ingest_item)                                as piezas,
                  (select count(*) from source)                                     as fuentes,
                  (select count(*) from source where state = 'ACTIVE')              as activas,
                  (select count(*) from media_asset)                                as medios
                """,
                (rs, n) -> new Totales(
                        rs.getLong("articulos"), rs.getLong("publicados"),
                        rs.getLong("borradores"), rs.getLong("piezas"),
                        rs.getLong("fuentes"), rs.getLong("activas"),
                        rs.getLong("medios")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PorFuente> porFuente() {
        // Los borradores se cuentan por la fuente de su pieza de ingesta; un artículo
        // subido a mano no tiene fuente y no debe aparecer aquí.
        return jdbc.query("""
                select s.name, s.state, s.failure_count, s.last_checked_at,
                       count(distinct i.id)                                            as piezas,
                       count(distinct a.id) filter (where a.state = 'PUBLISHED')        as publicadas,
                       count(distinct a.id) filter (where a.state = 'DRAFT')            as borradores
                from source s
                left join ingest_item i on i.source_id = s.id
                left join article a on a.ingest_item_id = i.id::text
                group by s.id, s.name, s.state, s.failure_count, s.last_checked_at
                order by piezas desc, s.name
                """,
                (rs, n) -> new PorFuente(
                        rs.getString("name"), rs.getString("state"),
                        rs.getLong("piezas"), rs.getLong("publicadas"), rs.getLong("borradores"),
                        rs.getTimestamp("last_checked_at") == null
                                ? null : rs.getTimestamp("last_checked_at").toInstant(),
                        rs.getInt("failure_count")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dia> ultimosDias(int dias) {
        // generate_series para que salgan también los días sin actividad: un hueco en
        // la gráfica dice más que una lista de días con datos.
        return jdbc.query("""
                with serie as (
                    select generate_series(
                        (current_date - (? || ' days')::interval)::date,
                        current_date, '1 day')::date as dia
                )
                select to_char(s.dia, 'DD/MM') as etiqueta,
                       count(distinct i.id) as piezas,
                       count(distinct a.id) filter (where a.state = 'PUBLISHED') as publicadas
                from serie s
                left join ingest_item i on i.created_at::date = s.dia
                left join article a on a.ingest_item_id = i.id::text
                group by s.dia
                order by s.dia
                """,
                (rs, n) -> new Dia(rs.getString("etiqueta"),
                        rs.getLong("piezas"), rs.getLong("publicadas")),
                Math.max(1, dias));
    }
}
