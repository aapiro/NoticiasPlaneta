package com.noticiasplaneta.ingest.application.service;

import com.noticiasplaneta.shared.events.IngestItemReceived;
import com.noticiasplaneta.ingest.application.port.in.IngestReport;
import com.noticiasplaneta.ingest.application.port.in.IngestSourceUseCase;
import com.noticiasplaneta.ingest.application.port.out.ExecutionLock;
import com.noticiasplaneta.ingest.application.port.out.FeedFetchOutcome;
import com.noticiasplaneta.ingest.application.port.out.FeedFetcher;
import com.noticiasplaneta.ingest.application.port.out.IngestItemRepository;
import com.noticiasplaneta.ingest.application.port.out.SourceRepository;
import com.noticiasplaneta.ingest.domain.ContentHash;
import com.noticiasplaneta.ingest.domain.FeedEntry;
import com.noticiasplaneta.ingest.domain.IngestItem;
import com.noticiasplaneta.ingest.domain.Source;
import com.noticiasplaneta.ingest.domain.SourceId;
import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;
import com.noticiasplaneta.shared.domain.DomainException;
import com.noticiasplaneta.shared.domain.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * F01 — Ingesta de fuentes.
 *
 * <p>Reglas aplicadas: RN1 (degradación tras 5 fallos), RN2 (deduplicación por externalId y por
 * huella dentro de la misma fuente), RN4 (truncado del cuerpo).
 */
public class IngestSourceService implements IngestSourceUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngestSourceService.class);

    /** Actor de las alertas del sistema: no hay persona detrás de una degradación. */
    private static final String ACTOR = "sistema";

    private final SourceRepository sources;
    private final IngestItemRepository items;
    private final FeedFetcher fetcher;
    private final ExecutionLock lock;
    private final EventPublisher events;
    private final AuditPort audit;
    private final Clock clock;

    public IngestSourceService(SourceRepository sources,
                               IngestItemRepository items,
                               FeedFetcher fetcher,
                               ExecutionLock lock,
                               EventPublisher events,
                               Clock clock,
                               AuditPort audit) {
        this.sources = sources;
        this.items = items;
        this.fetcher = fetcher;
        this.lock = lock;
        this.events = events;
        this.clock = clock;
        this.audit = audit;
    }

    @Override
    public IngestReport run(SourceId sourceId) {
        Source source = sources.findById(sourceId)
                .orElseThrow(() -> new DomainException("SOURCE_NOT_FOUND", "No existe la fuente " + sourceId));

        if (!source.isFetchable()) {
            return IngestReport.error("SOURCE_DISABLED");
        }

        String lockKey = "ingest:" + sourceId;
        if (!lock.tryAcquire(lockKey)) {
            return IngestReport.error("ALREADY_RUNNING");
        }

        try {
            return doRun(source);
        } finally {
            lock.release(lockKey);
        }
    }

    private IngestReport doRun(Source source) {
        Instant now = clock.instant();
        FeedFetchOutcome outcome = fetcher.fetch(source.feedUrl(), source.etag(), source.lastModified());

        return switch (outcome) {
            case FeedFetchOutcome.NotModified ignored -> {
                boolean recuperada = source.recordSuccess(now, source.etag(), source.lastModified());
                sources.save(source);
                if (recuperada) avisarRecuperacion(source, now);
                yield IngestReport.ofNotModified();
            }
            case FeedFetchOutcome.Failed failed -> {
                // RN1: al quinto fallo seguido la fuente pasa a DEGRADED. Ese quinto
                // fallo es el único que devuelve true, así que el aviso sale una vez.
                boolean degradada = source.recordFailure(now);
                sources.save(source);
                if (degradada) avisarDegradacion(source, failed.reason(), now);
                yield IngestReport.error(failed.reason());
            }
            case FeedFetchOutcome.Fetched fetched -> persist(source, fetched, now);
        };
    }

    private IngestReport persist(Source source, FeedFetchOutcome.Fetched fetched, Instant now) {
        int created = 0;
        int duplicated = 0;
        int rejected = 0;
        List<IngestItem> toPersist = new ArrayList<>();
        List<IngestItemReceived> eventsToPublish = new ArrayList<>();
        Set<String> seenExternalIds = new HashSet<>();
        Set<ContentHash> seenHashes = new HashSet<>();

        for (FeedEntry entry : fetched.entries()) {
            IngestItem item;
            try {
                item = IngestItem.receive(source.id(), entry);
            } catch (DomainException e) {
                rejected++;
                continue;
            }

            // El propio feed puede traer el mismo item repetido: si solo se comprueba
            // contra la BD, ambos pasan y el INSERT viola uq_ingest_item_source_external.
            boolean newExternalId = seenExternalIds.add(item.externalId());
            boolean newHash = seenHashes.add(item.contentHash());
            if (!newExternalId || !newHash) {
                duplicated++;
                continue;
            }

            if (items.existsBySourceAndExternalId(source.id(), item.externalId())
                    || items.existsBySourceAndContentHash(source.id(), item.contentHash())) {
                // El item ya está, pero puede que se ingiriera antes de leer Media RSS:
                // si el feed trae foto y en la base no hay, se rellena.
                items.backfillImage(source.id(), item.externalId(), item.imageUrl());
                duplicated++;
                continue;
            }

            toPersist.add(item);
            eventsToPublish.add(new IngestItemReceived(
                    item.id().value(),
                    source.id().value(),
                    item.canonicalUrl(),
                    item.publishedAt(),
                    item.contentHash().value()));
            created++;
        }

        items.saveAll(toPersist);
        boolean recuperada = source.recordSuccess(now, fetched.etag(), fetched.lastModified());
        sources.save(source);
        if (recuperada) avisarRecuperacion(source, now);

        eventsToPublish.forEach(events::publish);

        return new IngestReport(fetched.entries().size(), created, duplicated, rejected, false, null);
    }

    /** Una fuente rota en silencio es peor que una fuente caída: el aviso tiene que salir. */
    private void avisarDegradacion(Source source, String motivo, Instant ahora) {
        log.warn("fuente {} ({}) degradada tras {} fallos: {}",
                source.name(), source.id().value(), source.failureCount(), motivo);
        audit.record(new AuditEntry(ACTOR, "SYSTEM", "SOURCE_DEGRADED", "source",
                source.id().value().toString(), null, motivo, ahora));
    }

    private void avisarRecuperacion(Source source, Instant ahora) {
        log.info("fuente {} ({}) recuperada", source.name(), source.id().value());
        audit.record(new AuditEntry(ACTOR, "SYSTEM", "SOURCE_RECOVERED", "source",
                source.id().value().toString(), "DEGRADED", "ACTIVE", ahora));
    }
}
