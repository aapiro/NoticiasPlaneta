package com.noticiasplaneta.ingest;

import com.noticiasplaneta.ingest.application.port.in.BuscarMaterialUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@DisplayName("F8 · Búsqueda de material en el corpus ingerido")
class BuscarMaterialAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @Autowired
    BuscarMaterialUseCase buscar;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("Encuentra material por tema y devuelve medio, URL y fecha")
    void encuentraMaterialConMedioUrlYFecha() {
        UUID a = fuente("Diario A");
        UUID b = fuente("Diario B");
        item(a, "La sequía deja el embalse de Sau al 18 %", "La sequía ha dejado la capacidad del embalse de Sau al 18 % este mes.",
                Instant.now().minusSeconds(7200));
        item(b, "El Govern descarta cortes de agua", "El Govern descartó cortes de agua y pidió prudencia a los ayuntamientos.",
                Instant.now().minusSeconds(3600));
        item(a, "El tiempo en la costa", "Previsión de temperaturas suaves en el litoral durante el fin de semana.",
                Instant.now().minusSeconds(1800));

        List<BuscarMaterialUseCase.Material> encontrados = buscar.buscar("embalse Sau", 10);

        assertThat(encontrados).hasSize(1);
        BuscarMaterialUseCase.Material sau = encontrados.get(0);
        assertThat(sau.medio()).isEqualTo("Diario A");
        assertThat(sau.url()).startsWith("https://a.example/");
        assertThat(sau.fecha()).isNotNull();
        assertThat(sau.texto()).contains("18 %");
    }

    @Test
    @DisplayName("El respaldo por palabras encuentra lo que el índice de texto completo no casa")
    void respaldoPorPalabrasEncuentraParciales() {
        UUID a = fuente("Diario A");
        item(a, "Plan de autoconsumo", "La instalación fotovoltaica del edificio público entrará en servicio en marzo.",
                Instant.now().minusSeconds(3600));

        List<BuscarMaterialUseCase.Material> encontrados = buscar.buscar("fotovolt", 10);

        assertThat(encontrados).hasSize(1);
        assertThat(encontrados.get(0).titular()).isEqualTo("Plan de autoconsumo");
    }

    @Test
    @DisplayName("No devuelve material viejo: la ventana es de 30 días")
    void noDevuelveMaterialFueraDeVentana() {
        UUID a = fuente("Diario A");
        item(a, "Geotermia en el valle", "El proyecto de geotermia del valle se presentó hace dos meses.",
                Instant.now().minusSeconds(60L * 24 * 3600));

        assertThat(buscar.buscar("geotermia", 10)).isEmpty();
    }

    @Test
    @DisplayName("Sin coincidencias devuelve vacío, no inventa resultados")
    void sinCoincidenciasDevuelveVacio() {
        assertThat(buscar.buscar("ornitorrinco quantico", 10)).isEmpty();
        assertThat(buscar.buscar("", 10)).isEmpty();
    }

    private UUID fuente(String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into source (id, name, feed_url, state, failure_count) values (?,?,?,?,0)",
                id, nombre, "https://" + id + ".example/feed", "ACTIVE");
        return id;
    }

    private void item(UUID fuente, String titular, String cuerpo, Instant publicado) {
        jdbc.update("""
                        insert into ingest_item (id, source_id, external_id, content_hash, title, body,
                                                 canonical_url, published_at)
                        values (?,?,?,?,?,?,?,?)
                        """,
                UUID.randomUUID(), fuente, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                titular, cuerpo, "https://a.example/" + titular.toLowerCase().replaceAll("[^a-z]+", "-"),
                java.sql.Timestamp.from(publicado));
    }
}
