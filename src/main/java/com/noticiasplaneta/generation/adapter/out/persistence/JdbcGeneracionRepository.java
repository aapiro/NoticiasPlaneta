package com.noticiasplaneta.generation.adapter.out.persistence;

import com.noticiasplaneta.generation.application.port.out.GeneracionRepository;
import com.noticiasplaneta.generation.domain.Descarte;
import com.noticiasplaneta.generation.domain.Fragmento;
import com.noticiasplaneta.generation.domain.ParrafoVerificado;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de la trazabilidad (F8) en {@code article_generation}.
 *
 * <p>JSONB y JDBC directo: son datos de auditoría, no entidades de negocio. Se guardan
 * tal cual para poder responder después qué fragmentos vio el modelo y qué párrafos se
 * descartaron, sin reconstruirlo desde logs.
 */
@Component
class JdbcGeneracionRepository implements GeneracionRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    JdbcGeneracionRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public void save(Registro registro) {
        jdbc.update("""
                insert into article_generation (article_id, consulta, seccion, enfoque, prompt_version,
                                                modelo, proveedor, coste_micros, fuentes_independientes,
                                                fragmentos, parrafos, descartes)
                values (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
                on conflict (article_id) do update set
                    consulta = excluded.consulta,
                    seccion = excluded.seccion,
                    enfoque = excluded.enfoque,
                    prompt_version = excluded.prompt_version,
                    modelo = excluded.modelo,
                    proveedor = excluded.proveedor,
                    coste_micros = excluded.coste_micros,
                    fuentes_independientes = excluded.fuentes_independientes,
                    fragmentos = excluded.fragmentos,
                    parrafos = excluded.parrafos,
                    descartes = excluded.descartes
                """,
                registro.articleId(), registro.consulta(), registro.seccion(), registro.enfoque(),
                registro.promptVersion(), registro.modelo(), registro.proveedor(),
                registro.costeMicros(), registro.fuentesIndependientes(),
                fragmentosJson(registro.fragmentos()), parrafosJson(registro.parrafos()),
                descartesJson(registro.descartes()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Registro> findByArticleId(String articleId) {
        List<Registro> filas = jdbc.query("""
                        select article_id::text, consulta, seccion, enfoque, prompt_version, modelo,
                               proveedor, coste_micros, fuentes_independientes, fragmentos, parrafos, descartes
                          from article_generation
                         where article_id = ?::uuid
                        """,
                (rs, n) -> new Registro(
                        rs.getString("article_id"), rs.getString("consulta"), rs.getString("seccion"),
                        rs.getString("enfoque"), rs.getString("prompt_version"), rs.getString("modelo"),
                        rs.getString("proveedor"), rs.getLong("coste_micros"),
                        rs.getInt("fuentes_independientes"),
                        leerFragmentos(rs.getString("fragmentos")),
                        leerParrafos(rs.getString("parrafos")),
                        leerDescartes(rs.getString("descartes"))),
                articleId);
        return filas.stream().findFirst();
    }

    private String fragmentosJson(List<Fragmento> fragmentos) {
        var array = json.createArrayNode();
        for (Fragmento f : fragmentos) {
            var node = array.addObject();
            node.put("ref", f.ref());
            node.put("medio", f.medio());
            node.put("url", f.url());
            node.put("fecha", f.fecha() == null ? null : f.fecha().toString());
            node.put("titular", f.titular());
            node.put("texto", f.texto());
        }
        return json.writeValueAsString(array);
    }

    private String parrafosJson(List<ParrafoVerificado> parrafos) {
        var array = json.createArrayNode();
        for (ParrafoVerificado p : parrafos) {
            var node = array.addObject();
            node.put("texto", p.texto());
            var refs = node.putArray("citas");
            p.citas().forEach(refs::add);
        }
        return json.writeValueAsString(array);
    }

    private String descartesJson(List<Descarte> descartes) {
        var array = json.createArrayNode();
        for (Descarte d : descartes) {
            array.addObject().put("motivo", d.motivo()).put("detalle", d.detalle());
        }
        return json.writeValueAsString(array);
    }

    private List<Fragmento> leerFragmentos(String raw) {
        List<Fragmento> salida = new ArrayList<>();
        for (JsonNode node : json.readTree(raw == null ? "[]" : raw)) {
            String fecha = node.path("fecha").asString("");
            salida.add(new Fragmento(node.path("ref").asInt(),
                    node.path("medio").asString(""), node.path("url").asString(""),
                    fecha.isBlank() ? null : Instant.parse(fecha),
                    node.path("titular").asString(""), node.path("texto").asString("")));
        }
        return salida;
    }

    private List<ParrafoVerificado> leerParrafos(String raw) {
        List<ParrafoVerificado> salida = new ArrayList<>();
        for (JsonNode node : json.readTree(raw == null ? "[]" : raw)) {
            List<Integer> citas = new ArrayList<>();
            for (JsonNode ref : node.path("citas")) {
                citas.add(ref.asInt());
            }
            salida.add(new ParrafoVerificado(node.path("texto").asString(""), citas));
        }
        return salida;
    }

    private List<Descarte> leerDescartes(String raw) {
        List<Descarte> salida = new ArrayList<>();
        for (JsonNode node : json.readTree(raw == null ? "[]" : raw)) {
            salida.add(new Descarte(node.path("motivo").asString(""), node.path("detalle").asString("")));
        }
        return salida;
    }
}
