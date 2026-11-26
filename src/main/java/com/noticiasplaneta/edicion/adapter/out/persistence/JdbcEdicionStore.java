package com.noticiasplaneta.edicion.adapter.out.persistence;

import com.noticiasplaneta.edicion.application.port.out.EdicionStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC del panel de edición.
 *
 * <p>Va en SQL y no en JPA a propósito: el informe de una ejecución es una consulta analítica
 * sobre {@code article}, {@code ingest_item} y {@code section_config}, no un agregado que
 * alguien vaya a modificar campo a campo.
 */
@Component
class JdbcEdicionStore implements EdicionStore {

    private static final String COLUMNAS = """
            id, iniciada_en, terminada_en, actor, modo, estado, fuentes, fuentes_con_error,
            creadas, borradores, publicadas, errores, detalle
            """;

    private static final RowMapper<EjecucionFila> FILA = (rs, n) -> new EjecucionFila(
            rs.getObject("id", UUID.class),
            instante(rs.getTimestamp("iniciada_en")),
            instante(rs.getTimestamp("terminada_en")),
            rs.getString("actor"),
            rs.getString("modo"),
            rs.getString("estado"),
            rs.getInt("fuentes"),
            rs.getInt("fuentes_con_error"),
            rs.getInt("creadas"),
            rs.getInt("borradores"),
            rs.getInt("publicadas"),
            rs.getInt("errores"),
            rs.getString("detalle"));

    private final JdbcTemplate jdbc;

    JdbcEdicionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> abrir(UUID id, Instant inicio, String actor, String modo) {
        // Una ejecución que quedó EN_CURSO porque el proceso murió bloquea el panel para
        // siempre si no se cierra sola. Media hora es de sobra para 8 fuentes.
        jdbc.update("""
                update edicion_ejecucion
                   set estado = 'FALLIDA',
                       terminada_en = now(),
                       detalle = trim(both E'\n' from coalesce(detalle || E'\n', '')
                                 || 'ejecución abandonada: el proceso murió antes de cerrarla')
                 where estado = 'EN_CURSO' and iniciada_en < now() - interval '30 minutes'
                """);

        try {
            // El índice único parcial uq_edicion_en_curso es el candado real.
            jdbc.update("""
                    insert into edicion_ejecucion (id, iniciada_en, actor, modo, estado)
                    values (?, ?, ?, ?, 'EN_CURSO')
                    """, id, Timestamp.from(inicio), actor, modo);
            return Optional.of(id);
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
    }

    @Override
    public void cerrar(Cierre c) {
        jdbc.update("""
                update edicion_ejecucion
                   set terminada_en = ?, estado = ?, fuentes = ?, fuentes_con_error = ?,
                       creadas = ?, borradores = ?, publicadas = ?, errores = ?, detalle = ?
                 where id = ?
                """, Timestamp.from(c.fin()), c.estado(), c.fuentes(), c.fuentesConError(),
                c.creadas(), c.borradores(), c.publicadas(), c.errores(), c.detalle(), c.id());
    }

    @Override
    public boolean enCurso() {
        Boolean existe = jdbc.queryForObject(
                "select exists (select 1 from edicion_ejecucion where estado = 'EN_CURSO')",
                Boolean.class);
        return Boolean.TRUE.equals(existe);
    }

    @Override
    public Optional<EjecucionFila> ejecucion(UUID id) {
        List<EjecucionFila> filas = jdbc.query(
                "select " + COLUMNAS + " from edicion_ejecucion where id = ?", FILA, id);
        return filas.stream().findFirst();
    }

    @Override
    public List<EjecucionFila> ejecuciones(int limite) {
        return jdbc.query("select " + COLUMNAS
                + " from edicion_ejecucion order by iniciada_en desc limit ?", FILA, limite);
    }

    @Override
    public List<PiezaFila> piezas(UUID ejecucionId) {
        return jdbc.query("""
                        select article_id, section_id, titulo
                          from edicion_ejecucion_pieza
                         where ejecucion_id = ?
                         order by posicion, titulo
                        """,
                (rs, n) -> new PiezaFila(
                        rs.getObject("article_id", UUID.class).toString(),
                        rs.getString("section_id"),
                        rs.getString("titulo")),
                ejecucionId);
    }

    @Override
    public void guardarPiezas(UUID ejecucionId, List<PiezaFila> piezas) {
        for (int i = 0; i < piezas.size(); i++) {
            PiezaFila p = piezas.get(i);
            jdbc.update("""
                    insert into edicion_ejecucion_pieza
                        (ejecucion_id, article_id, section_id, titulo, posicion)
                    values (?, ?::uuid, ?, ?, ?)
                    on conflict (ejecucion_id, article_id) do nothing
                    """, ejecucionId, p.articleId(), p.sectionId(), p.titulo(), i);
        }
    }

    @Override
    public int borradoresCreadosDesde(Instant desde) {
        Integer n = jdbc.queryForObject(
                "select count(*) from article where created_at >= ?", Integer.class, Timestamp.from(desde));
        return n == null ? 0 : n;
    }

    @Override
    public List<Candidato> candidatos(Instant desde, int limite, int maxPorFuente) {
        return jdbc.query("""
                        with candidatos as (
                            select a.id, a.title, a.section_id,
                                   sc.max_piezas_por_edicion as cupo,
                                   coalesce(i.published_at, i.created_at) as fecha,
                                   row_number() over (partition by i.source_id
                                       order by coalesce(i.published_at, i.created_at) desc) as rn_fuente,
                                   row_number() over (partition by a.section_id
                                       order by coalesce(i.published_at, i.created_at) desc) as rn_seccion
                              from article a
                              join ingest_item i on i.id::text = a.ingest_item_id
                              join section_config sc on sc.slug = a.section_id and sc.activa
                             where a.state = 'DRAFT'
                               and i.image_url is not null
                               and coalesce(i.published_at, i.created_at) > ?
                        )
                        select id, title, section_id
                          from candidatos
                         where rn_fuente <= ? and rn_seccion <= cupo
                         order by fecha desc
                         limit ?
                        """,
                (rs, n) -> new Candidato(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("title"),
                        rs.getString("section_id")),
                Timestamp.from(desde), maxPorFuente, limite);
    }

    @Override
    public Map<String, Integer> piezasPublicadasPorSeccion(Instant desde) {
        Map<String, Integer> porSeccion = new HashMap<>();
        jdbc.query("""
                        select section_id, count(*) as piezas
                          from article
                         where state = 'PUBLISHED' and published_at >= ?
                         group by section_id
                        """,
                rs -> {
                    porSeccion.put(rs.getString("section_id"), rs.getInt("piezas"));
                },
                Timestamp.from(desde));
        return porSeccion;
    }

    @Override
    public long totalPublicadas() {
        Long n = jdbc.queryForObject(
                "select count(*) from article where state = 'PUBLISHED'", Long.class);
        return n == null ? 0 : n;
    }

    private static Instant instante(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
