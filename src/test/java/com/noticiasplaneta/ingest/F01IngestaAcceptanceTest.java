package com.noticiasplaneta.ingest;

import com.noticiasplaneta.shared.events.IngestItemReceived;
import com.noticiasplaneta.ingest.application.port.in.AddSourceUseCase;
import com.noticiasplaneta.ingest.application.port.in.IngestReport;
import com.noticiasplaneta.ingest.application.port.in.IngestSourceUseCase;
import com.noticiasplaneta.ingest.application.port.out.FeedFetchOutcome;
import com.noticiasplaneta.ingest.application.port.out.FeedFetcher;
import com.noticiasplaneta.ingest.application.port.out.SourceRepository;
import com.noticiasplaneta.ingest.domain.FeedEntry;
import com.noticiasplaneta.ingest.domain.Source;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Criterios de aceptación de F01 ejecutados contra PostgreSQL real (Testcontainers)
 * y Flyway aplicado de verdad. El feed se sustituye por un doble para no depender de red.
 */
@SpringBootTest
@Testcontainers
@DisplayName("F01 · Aceptación: ingesta de fuentes")
class F01IngestaAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @TestConfiguration
    static class StubFeed {
        @Bean
        @Primary
        FeedFetcher stubFeedFetcher() {
            return new FeedFetcher() {
                @Override
                public FeedFetchOutcome fetch(String feedUrl, String etag, String lastModified) {
                    return new FeedFetchOutcome.Fetched(List.of(
                            new FeedEntry("a", "Reforma fiscal aprobada", "El Congreso aprueba la reforma.",
                                    "https://ejemplo.es/a", Instant.parse("2026-11-05T09:00:00Z"), "Redacción"),
                            new FeedEntry("b", "Sube la luz un 12%", "La factura sube.",
                                    "https://ejemplo.es/b", Instant.parse("2026-11-05T10:00:00Z"), "Redacción"),
                            new FeedEntry("c", "La selección gana", "Gol en el minuto 90.",
                                    "https://ejemplo.es/c", Instant.parse("2026-11-05T11:00:00Z"), "Redacción")
                    ), "etag-1", null);
                }
            };
        }
    }

    @Autowired
    AddSourceUseCase addSource;

    @Autowired
    IngestSourceUseCase ingesta;

    @Autowired
    SourceRepository sources;

    @Test
    @DisplayName("Escenario: se importan los items de una fuente activa")
    void importaItems() {
        Source source = addSource.add("BOE", "https://ejemplo.es/rss");

        IngestReport report = ingesta.run(source.id());

        assertThat(report.fetched()).isEqualTo(3);
        assertThat(report.created()).isEqualTo(3);
        assertThat(report.duplicated()).isZero();
        assertThat(report.error()).isNull();
    }

    @Test
    @DisplayName("Escenario: una segunda pasada no duplica nada")
    void segundaPasadaNoDuplica() {
        Source source = addSource.add("El País", "https://ejemplo.es/rss2");

        ingesta.run(source.id());
        IngestReport segunda = ingesta.run(source.id());

        assertThat(segunda.created()).isZero();
        assertThat(segunda.duplicated()).isEqualTo(3);
    }

    @Test
    @DisplayName("Escenario: tras la ingesta la fuente queda con last_checked_at y sigue ACTIVE")
    void fuenteMarcada() {
        Source source = addSource.add("Europa Press", "https://ejemplo.es/rss3");

        ingesta.run(source.id());

        Source recargada = sources.findById(source.id()).orElseThrow();
        assertThat(recargada.lastCheckedAt()).isNotNull();
        assertThat(recargada.failureCount()).isZero();
        assertThat(recargada.etag()).isEqualTo("etag-1");
    }
}
