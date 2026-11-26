package com.noticiasplaneta.generation;

import com.noticiasplaneta.ai.application.port.out.AiCapabilitiesAdminPort;
import com.noticiasplaneta.ai.domain.AiCapability;
import com.noticiasplaneta.ai.domain.AiMode;
import com.noticiasplaneta.generation.application.port.in.GenerarBorradorCommand;
import com.noticiasplaneta.generation.application.port.in.GenerarBorradorUseCase;
import com.noticiasplaneta.generation.application.port.in.ResultadoGeneracion;
import com.noticiasplaneta.generation.application.port.out.RedaccionPort;
import com.noticiasplaneta.generation.domain.BorradorSinVerificar;
import com.noticiasplaneta.generation.domain.ParrafoSinVerificar;
import com.noticiasplaneta.news.application.port.out.UrlChecker;
import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F8 de punta a punta contra PostgreSQL real: corpus ingerido, secciones de la migración,
 * contraste de F04, auditoría y publicación automática.
 *
 * <p>El único doble es el proveedor de IA (no hay clave en esta máquina) y el comprobador
 * de URLs (evita salir a internet desde un test). Todo lo demás es el sistema de verdad.
 */
@SpringBootTest
@Testcontainers
@Import(F8GeneradorAcceptanceTest.Stubs.class)
@DisplayName("F8 · Aceptación: generador desde un tema")
class F8GeneradorAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    static final RedaccionStub REDACCION = new RedaccionStub();

    @TestConfiguration
    static class Stubs {

        @Bean
        @Primary
        RedaccionPort redaccionPort() {
            return REDACCION;
        }

        @Bean
        @Primary
        UrlChecker urlChecker() {
            return url -> new UrlChecker.Check(200, true, Instant.now());
        }
    }

    @Autowired
    GenerarBorradorUseCase generador;

    @Autowired
    SectionAdminUseCase secciones;

    @Autowired
    AiCapabilitiesAdminPort capacidades;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void sembrar() {
        REDACCION.reiniciar();
        UUID a = fuente("Diario A");
        UUID b = fuente("Diario B");
        item(a, "La sequía deja el embalse de Sau al 18 %",
                "La sequía ha dejado la capacidad del embalse de Sau al 18 % este mes en Cataluña, según la agencia "
                        + "catalana del agua, y la situación afecta al abastecimiento de 1.800 vecinos de la comarca.",
                "https://a.example/sau");
        item(b, "El Govern descarta cortes de agua por la sequía",
                "El Govern descartó cortes de agua por ahora, con los embalses de las cuencas internas al 19 %, "
                        + "y pidió prudencia a los ayuntamientos de las comarcas afectadas por la sequía.",
                "https://b.example/govern");
    }

    @AfterEach
    void limpiarInterruptores() {
        capacidades.setMode(AiCapability.PUBLISH_AUTO, AiMode.OFF, "test");
        secciones.setAutoPublica("economia", false);
    }

    @Test
    @DisplayName("Un tema con cobertura real produce un borrador rastreable con sus fuentes")
    void produceBorradorRastreable() {
        ResultadoGeneracion r = generador.generar(
                GenerarBorradorCommand.humano("sequía embalses", "economia", "editor@test"));

        assertThat(r.estado()).withFailMessage(() -> "abortado: " + r.motivo() + " · " + r.explicacion())
                .isEqualTo(ResultadoGeneracion.Estado.BORRADOR);
        assertThat(r.articleId()).isNotNull();
        assertThat(r.titular()).isNotBlank();
        assertThat(r.fuentes()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(r.promptVersion()).isEqualTo("generar-v1");
        assertThat(REDACCION.llamadas.get()).isEqualTo(1);

        Map<String, Object> articulo = jdbc.queryForMap(
                "select state, origin, body from article where id = ?::uuid", r.articleId());
        assertThat(articulo.get("state")).isEqualTo("DRAFT");
        assertThat(articulo.get("origin")).isEqualTo("AGENT");
        assertThat((String) articulo.get("body")).contains("Texto generado con IA a partir de")
                .contains("Pendiente de revisión editorial");

        Integer fuentes = jdbc.queryForObject(
                "select count(*) from article_source where article_id = ?::uuid", Integer.class, r.articleId());
        assertThat(fuentes).isGreaterThanOrEqualTo(2);
        Integer independientes = jdbc.queryForObject(
                "select independent_source_count from article where id = ?::uuid", Integer.class, r.articleId());
        assertThat(independientes).isGreaterThanOrEqualTo(2);

        Map<String, Object> traza = jdbc.queryForMap("""
                select consulta, seccion, enfoque, prompt_version, modelo, fuentes_independientes,
                       jsonb_array_length(fragmentos) as fragmentos,
                       jsonb_array_length(parrafos) as parrafos
                  from article_generation where article_id = ?::uuid
                """, r.articleId());
        assertThat(traza.get("consulta")).isEqualTo("sequía embalses");
        assertThat(traza.get("seccion")).isEqualTo("economia");
        assertThat(traza.get("enfoque")).isEqualTo("NEUTRAL");
        assertThat(traza.get("prompt_version")).isEqualTo("generar-v1");
        assertThat((Integer) traza.get("fragmentos")).isGreaterThanOrEqualTo(2);
        assertThat((Integer) traza.get("parrafos")).isGreaterThanOrEqualTo(1);

        Integer auditoria = jdbc.queryForObject(
                "select count(*) from audit_log where action = 'ARTICLE_GENERATED_DRAFT' and entity_id = ?",
                Integer.class, r.articleId());
        assertThat(auditoria).isEqualTo(1);
    }

    @Test
    @DisplayName("Un tema sin cobertura no inventa: aborta, lo explica y no crea artículo")
    void sinCoberturaNoInventa() {
        long antes = contarArticulos();

        ResultadoGeneracion r = generador.generar(
                GenerarBorradorCommand.humano("fusión nuclear de helio en Marte", "economia", "editor@test"));

        assertThat(r.estado()).isEqualTo(ResultadoGeneracion.Estado.ABORTADO);
        assertThat(r.motivo()).isEqualTo("sin_material");
        assertThat(r.explicacion()).isNotBlank();
        assertThat(r.articleId()).isNull();
        assertThat(REDACCION.llamadas.get()).isZero();
        assertThat(contarArticulos()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Con una sola fuente independiente aborta antes de llamar al modelo")
    void unaSolaFuenteAborta() {
        long antes = contarArticulos();

        ResultadoGeneracion r = generador.generar(
                GenerarBorradorCommand.humano("el embalse de Sau", "economia", "editor@test"));

        assertThat(r.motivo()).isEqualTo("menos_de_2_fuentes_independientes");
        assertThat(r.fuentesIndependientes()).isEqualTo(1);
        assertThat(REDACCION.llamadas.get()).isZero();
        assertThat(contarArticulos()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Con PUBLISH_AUTO en la sección y la capacidad encendida, publica")
    void publicaConPublishAuto() {
        secciones.setAutoPublica("economia", true);
        capacidades.setMode(AiCapability.PUBLISH_AUTO, AiMode.EXTERNAL, "test");

        ResultadoGeneracion r = generador.generar(
                GenerarBorradorCommand.humano("sequía embalses", "economia", "editor@test"));

        assertThat(r.estado()).withFailMessage(() -> "abortado: " + r.motivo() + " · " + r.explicacion())
                .isEqualTo(ResultadoGeneracion.Estado.PUBLICADO);
        assertThat(r.publicado()).isTrue();
        String estado = jdbc.queryForObject(
                "select state from article where id = ?::uuid", String.class, r.articleId());
        assertThat(estado).isEqualTo("PUBLISHED");
        Integer auto = jdbc.queryForObject(
                "select count(*) from audit_log where action = 'ARTICLE_AUTO_PUBLISHED' and entity_id = ?",
                Integer.class, r.articleId());
        assertThat(auto).isEqualTo(1);
        Integer sinRevision = jdbc.queryForObject(
                "select count(*) from audit_log where action = 'ARTICLE_PUBLISHED_WITHOUT_REVIEW' and entity_id = ?",
                Integer.class, r.articleId());
        assertThat(sinRevision).isEqualTo(1);
    }

    @Test
    @DisplayName("Un agente nunca publica, aunque la sección tenga PUBLISH_AUTO")
    void agenteNoPublica() {
        secciones.setAutoPublica("economia", true);
        capacidades.setMode(AiCapability.PUBLISH_AUTO, AiMode.EXTERNAL, "test");

        ResultadoGeneracion r = generador.generar(
                GenerarBorradorCommand.agente("sequía embalses", "economia", "agente-mcp"));

        assertThat(r.estado()).isEqualTo(ResultadoGeneracion.Estado.BORRADOR);
        String estado = jdbc.queryForObject(
                "select state from article where id = ?::uuid", String.class, r.articleId());
        assertThat(estado).isEqualTo("DRAFT");
    }

    private long contarArticulos() {
        Long total = jdbc.queryForObject("select count(*) from article", Long.class);
        return total == null ? 0 : total;
    }

    private UUID fuente(String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into source (id, name, feed_url, state, failure_count) values (?,?,?,?,0)",
                id, nombre, "https://" + id + ".example/feed", "ACTIVE");
        return id;
    }

    private void item(UUID fuente, String titular, String cuerpo, String url) {
        jdbc.update("""
                        insert into ingest_item (id, source_id, external_id, content_hash, title, body,
                                                 canonical_url, published_at)
                        values (?,?,?,?,?,?,?,?)
                        """,
                UUID.randomUUID(), fuente, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                titular, cuerpo, url, java.sql.Timestamp.from(Instant.now().minusSeconds(3600)));
    }

    /** Doble del proveedor: devuelve párrafos apoyados en cada fragmento recibido. */
    static class RedaccionStub implements RedaccionPort {

        final AtomicInteger llamadas = new AtomicInteger();
        volatile boolean inventarCifra = false;

        void reiniciar() {
            llamadas.set(0);
            inventarCifra = false;
        }

        @Override
        public Resultado redactar(Peticion peticion) {
            llamadas.incrementAndGet();
            List<ParrafoSinVerificar> parrafos = new ArrayList<>();
            for (Fuente fuente : peticion.fuentes()) {
                String texto = fuente.texto();
                if (inventarCifra) {
                    texto = texto + " El informe cifra la pérdida en 987 hectómetros cúbicos.";
                }
                parrafos.add(new ParrafoSinVerificar(texto, List.of(fuente.ref())));
            }
            return new Resultado(
                    new BorradorSinVerificar("La sequía deja los embalses de Cataluña al 18 %", parrafos),
                    "stub-model", "generar-v1", "stub", 0);
        }
    }
}
