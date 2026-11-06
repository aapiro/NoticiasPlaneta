package com.noticiasplaneta.ingest.application;

import com.noticiasplaneta.ingest.application.event.IngestItemReceived;
import com.noticiasplaneta.ingest.application.port.in.IngestReport;
import com.noticiasplaneta.ingest.application.port.out.FeedFetchOutcome;
import com.noticiasplaneta.ingest.application.service.IngestSourceService;
import com.noticiasplaneta.ingest.domain.FeedEntry;
import com.noticiasplaneta.ingest.domain.Source;
import com.noticiasplaneta.ingest.domain.SourceState;
import com.noticiasplaneta.ingest.support.InMemoryEventPublisher;
import com.noticiasplaneta.ingest.support.InMemoryItems;
import com.noticiasplaneta.ingest.support.InMemoryLock;
import com.noticiasplaneta.ingest.support.InMemorySources;
import com.noticiasplaneta.ingest.support.StubFeedFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("F01 · Caso de uso: ingesta de fuentes")
class IngestSourceServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");

    private InMemorySources sources;
    private InMemoryItems items;
    private StubFeedFetcher fetcher;
    private InMemoryEventPublisher events;
    private IngestSourceService service;
    private Source source;

    @BeforeEach
    void setUp() {
        sources = new InMemorySources();
        items = new InMemoryItems();
        fetcher = new StubFeedFetcher();
        events = new InMemoryEventPublisher();
        service = new IngestSourceService(
                sources, items, fetcher, new InMemoryLock(), events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        source = sources.save(Source.register("BOE", "https://boe.es/rss"));
    }

    private FeedEntry entry(String id, String title, String url) {
        return new FeedEntry(id, title, "cuerpo de " + title, url, NOW, "Redacción");
    }

    @Test
    @DisplayName("Se importan solo los items nuevos")
    void soloLosNuevos() {
        items.seed(com.noticiasplaneta.ingest.domain.IngestItem.receive(source.id(),
                entry("a", "Noticia A", "https://boe.es/a")));
        items.seed(com.noticiasplaneta.ingest.domain.IngestItem.receive(source.id(),
                entry("b", "Noticia B", "https://boe.es/b")));

        fetcher.willReturn(new FeedFetchOutcome.Fetched(List.of(
                entry("a", "Noticia A", "https://boe.es/a"),
                entry("b", "Noticia B", "https://boe.es/b"),
                entry("c", "Noticia C", "https://boe.es/c")), "etag-2", null));

        IngestReport report = service.run(source.id());

        assertThat(report.fetched()).isEqualTo(3);
        assertThat(report.created()).isEqualTo(1);
        assertThat(report.duplicated()).isEqualTo(2);
        assertThat(report.rejected()).isZero();
        assertThat(items.count()).isEqualTo(3);
        assertThat(events.published()).hasSize(1)
                .first().isInstanceOf(IngestItemReceived.class);
    }

    @Test
    @DisplayName("La misma huella en la misma fuente no se duplica aunque cambie el externalId")
    void mismaHuellaMismaFuente() {
        items.seed(com.noticiasplaneta.ingest.domain.IngestItem.receive(source.id(),
                entry("viejo", "Noticia repetida", "https://boe.es/x")));

        fetcher.willReturn(new FeedFetchOutcome.Fetched(List.of(
                entry("nuevo-id", "Noticia repetida", "https://boe.es/x")), null, null));

        IngestReport report = service.run(source.id());

        assertThat(report.duplicated()).isEqualTo(1);
        assertThat(report.created()).isZero();
    }

    @Test
    @DisplayName("304 no es un error y actualiza la marca de comprobación")
    void notModifiedNoEsError() {
        fetcher.willReturn(new FeedFetchOutcome.NotModified());

        IngestReport report = service.run(source.id());

        assertThat(report.notModified()).isTrue();
        assertThat(report.error()).isNull();
        assertThat(sources.findById(source.id()).orElseThrow().lastCheckedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Un timeout incrementa el contador de fallos y devuelve error")
    void timeoutIncrementaFallos() {
        fetcher.willReturn(new FeedFetchOutcome.Failed("TIMEOUT"));

        IngestReport report = service.run(source.id());

        assertThat(report.error()).isEqualTo("TIMEOUT");
        assertThat(sources.findById(source.id()).orElseThrow().failureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cinco fallos consecutivos dejan la fuente DEGRADED")
    void cincoFallosDegradan() {
        fetcher.willReturn(new FeedFetchOutcome.Failed("HTTP_503"));

        for (int i = 0; i < Source.DEGRADE_THRESHOLD; i++) {
            service.run(source.id());
        }

        assertThat(sources.findById(source.id()).orElseThrow().state()).isEqualTo(SourceState.DEGRADED);
    }

    @Test
    @DisplayName("Un item sin título se descarta y se contabiliza como rechazado")
    void tituloVacioSeRechaza() {
        fetcher.willReturn(new FeedFetchOutcome.Fetched(List.of(
                entry("x", "   ", "https://boe.es/x"),
                entry("y", "Válido", "https://boe.es/y")), null, null));

        IngestReport report = service.run(source.id());

        assertThat(report.rejected()).isEqualTo(1);
        assertThat(report.created()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una segunda ejecución concurrente sobre la misma fuente no hace nada")
    void ejecucionConcurrente() {
        InMemoryLock lock = new InMemoryLock();
        lock.tryAcquire("ingest:" + source.id());
        IngestSourceService concurrente = new IngestSourceService(
                sources, items, fetcher, lock, events, Clock.fixed(NOW, ZoneOffset.UTC));

        IngestReport report = concurrente.run(source.id());

        assertThat(report.error()).isEqualTo("ALREADY_RUNNING");
        assertThat(items.count()).isZero();
    }

    @Test
    @DisplayName("Una fuente desactivada no se consulta")
    void desactivada() {
        Source disabled = sources.save(Source.register("Off", "https://off.es/rss"));
        disabled.disable();
        sources.save(disabled);

        assertThat(service.run(disabled.id()).error()).isEqualTo("SOURCE_DISABLED");
    }
}
