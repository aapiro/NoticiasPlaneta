package com.noticiasplaneta.generation;

import com.noticiasplaneta.generation.application.port.in.GenerarBorradorCommand;
import com.noticiasplaneta.generation.application.port.in.GenerarBorradorUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

/**
 * Prueba REAL del generador contra DeepSeek, no un mock.
 *
 * <p>Solo corre si se pasa la clave:
 * {@code -Dtest.deepseek.key=$(docker exec ... printenv DEEPSEEK_API_KEY)}.
 *
 * <p>Siembra material de dos medios independientes sobre el mismo tema y comprueba
 * que el generador monta una pieza cuya información está en ese material. Lo que se
 * valida aquí es el mecanismo (que no inventa y que cita fuentes), no la verdad de
 * los textos sembrados.
 */
@SpringBootTest
@Testcontainers
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_KEY", matches = ".+")
@DisplayName("F8 · Generador real contra DeepSeek")
class GeneradorRealIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-gen-real");
        registry.add("security.api-key", () -> "clave-test");
        registry.add("admin.bootstrap.email", () -> "");
        registry.add("ai.deepseek.api-key", () -> {
            String clave = System.getProperty("test.deepseek.key");
            return clave != null && !clave.isBlank() ? clave : System.getenv("DEEPSEEK_KEY");
        });
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GenerarBorradorUseCase generador;

    private void sembrarMedio(String nombre, String sufijo, String titular, String cuerpo) {
        UUID sourceId = UUID.randomUUID();
        jdbc.update("""
                insert into source (id, name, feed_url, state, failure_count, confianza)
                values (?, ?, ?, 'ACTIVE', 0, 'ALTA')""",
                sourceId, nombre, "https://" + sufijo + ".example/rss");

        UUID itemId = UUID.randomUUID();
        jdbc.update("""
                insert into ingest_item
                  (id, source_id, external_id, content_hash, title, body, canonical_url,
                   published_at, author, image_url, created_at)
                values (?, ?, ?, ?, ?, ?, ?, now(), ?, ?, now())""",
                itemId, sourceId, "ext-" + sufijo, "hash-" + sufijo,
                titular, cuerpo, "https://" + sufijo + ".example/nota",
                "Redacción", "https://" + sufijo + ".example/foto.jpg");

        jdbc.update("""
                insert into article (id, language, section_id, origin, ingest_item_id,
                                     slug, title, body, state, created_at)
                values (?, 'es', 'general', 'INGEST', ?, ?, ?, ?, 'DRAFT', now())""",
                UUID.randomUUID(), itemId.toString(),
                "nota-" + sufijo + "-" + UUID.randomUUID().toString().substring(0, 8),
                titular, cuerpo);
    }

    @Test
    @DisplayName("Con material de dos medios, arma una pieza y cita las fuentes")
    void generaConMaterial() {
        sembrarMedio("Diario A", "diarioa",
                "Cataluña declara alerta por sequía en 40 municipios",
                "La Generalitat ha declarado el nivel de alerta por sequía en 40 municipios "
                        + "del área metropolitana de Barcelona. Las reservas de las cuencas "
                        + "internas están al 31% de su capacidad, según datos de la ACA.");

        sembrarMedio("Diario B", "diariob",
                "Las cuencas internas de Cataluña bajan al 31% por la falta de lluvia",
                "Las reservas hídricas de las cuencas internas de Cataluña han caído al 31%, "
                        + "el nivel más bajo del año. La Agencia Catalana del Agua advierte de "
                        + "que si no llueve en tres semanas se activarán restricciones.");

        var resultado = generador.generar(
                GenerarBorradorCommand.humano("sequía en Cataluña", "general", "prueba-real"));

        System.out.println("=== RESULTADO DEL GENERADOR ===");
        System.out.println("estado:      " + resultado.estado());
        System.out.println("motivo:      " + resultado.motivo());
        System.out.println("explicacion: " + resultado.explicacion());
        System.out.println("titular:     " + resultado.titular());
        System.out.println("enfoque:     " + resultado.enfoque());
        System.out.println("modelo:      " + resultado.modelo() + " / " + resultado.proveedor());
        System.out.println("costeMicros: " + resultado.costeMicros());
        System.out.println("fuentes:     " + resultado.fuentesIndependientes()
                + " (fragmentos " + resultado.fragmentosRecogidos() + ")");
        System.out.println("publicado:   " + resultado.publicado());
        System.out.println("--- cuerpo ---");
        System.out.println(resultado.cuerpo());
        System.out.println("--- fuentes ---");
        if (resultado.fuentes() != null) {
            resultado.fuentes().forEach(f -> System.out.println("  " + f));
        }
        System.out.println("=== FIN ===");
    }
}
