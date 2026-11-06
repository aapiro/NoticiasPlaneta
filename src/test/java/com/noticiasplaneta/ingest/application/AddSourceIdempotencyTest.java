package com.noticiasplaneta.ingest.application;

import com.noticiasplaneta.ingest.application.port.in.AddSourceUseCase;
import com.noticiasplaneta.ingest.domain.Source;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@DisplayName("F01 · Un feed no se registra dos veces")
class AddSourceIdempotencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @Autowired
    AddSourceUseCase addSource;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    @DisplayName("Registrar el mismo feed dos veces devuelve la misma fuente")
    void mismoFeed() {
        String feed = "https://ejemplo.es/rss/duplicado-" + java.util.UUID.randomUUID() + ".xml";

        Source primera = addSource.add("Ejemplo", feed);
        Source segunda = addSource.add("Ejemplo (otra vez)", feed);

        assertThat(segunda.id()).isEqualTo(primera.id());
        Integer filas = jdbc.queryForObject(
                "select count(*) from source where feed_url = ?", Integer.class, feed);
        assertThat(filas).isEqualTo(1);
    }

    @Test
    @DisplayName("Un feed distinto sí crea una fuente nueva")
    void feedDistinto() {
        String base = "https://ejemplo.es/rss/" + java.util.UUID.randomUUID();
        Source a = addSource.add("A", base + "-a.xml");
        Source b = addSource.add("B", base + "-b.xml");
        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test
    @DisplayName("La base rechaza dos fuentes activas con el mismo feed")
    void restriccionEnBase() {
        String feed = "https://ejemplo.es/rss/unico-" + java.util.UUID.randomUUID() + ".xml";
        addSource.add("Original", feed);

        assertThatThrownBy(() -> jdbc.update(
                "insert into source (id, name, feed_url, state, failure_count) values (?,?,?,?,0)",
                java.util.UUID.randomUUID(), "Copia", feed, "ACTIVE"))
                .as("el índice único parcial debe impedirlo")
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
