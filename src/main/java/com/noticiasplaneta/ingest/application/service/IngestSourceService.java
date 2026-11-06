package com.noticiasplaneta.ingest.application.service;

import com.noticiasplaneta.ingest.application.event.IngestItemReceived;
import com.noticiasplaneta.ingest.application.port.in.IngestReport;
import com.noticiasplaneta.ingest.application.port.in.IngestSourceUseCase;
import com.noticiasplaneta.ingest.application.port.out.ExecutionLock;
import com.noticiasplaneta.ingest.application.port.out.FeedFetchOutcome;
import com.noticiasplaneta.ingest.application.port.out.FeedFetcher;
import com.noticiasplaneta.ingest.application.port.out.IngestItemRepository;
import com.noticiasplaneta.ingest.application.port.out.SourceRepository;
import com.noticiasplaneta.ingest.domain.FeedEntry;
import com.noticiasplaneta.ingest.domain.IngestItem;
import com.noticiasplaneta.ingest.domain.Source;
import com.noticiasplaneta.ingest.domain.SourceId;
import com.noticiasplaneta.shared.domain.DomainException;
import com.noticiasplaneta.shared.domain.EventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * F01 — Ingesta de fuentes.
 *
 * <p>Reglas aplicadas: RN1 (degradación tras 5 fallos), RN2 (deduplicación por externalId y por
 * huella dentro de la misma fuente), RN4 (truncado del cuerpo).
 */
public class IngestSourceService implements IngestSourceUseCase {

    private final SourceRepository sources;
    private final IngestItemRepository items;
    private final FeedFetcher fetcher;
    private final ExecutionLock lock;
    private final EventPublisher events;
    private final Clock clock;

    public IngestSourceService(SourceRepository sources,
                               IngestItemRepository items,
                               FeedFetcher fetcher,
                               ExecutionLock lock,
                               EventPublisher events,
                               Clock clock) {
        this.sources = sources;
        this.items = items;
        this.fetcher = fetcher;
        this.lock = lock;
        this.events = events;
        this.clock = clock;
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
                source.recordSuccess(now, source.etag(), source.lastModified());
                sources.save(source);
                yield IngestReport.ofNotModified();
            }
            case FeedFetchOutcome.Failed failed -> {
                source.recordFailure(now);
                sources.save(source);
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

        for (FeedEntry entry : fetched.entries()) {
            IngestItem item;
            try {
                item = IngestItem.receive(source.id(), entry);
            } catch (DomainException e) {
                rejected++;
                continue;
            }

            if (items.existsBySourceAndExternalId(source.id(), item.externalId())
                    || items.existsBySourceAndContentHash(source.id(), item.contentHash())) {
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
        source.recordSuccess(now, fetched.etag(), fetched.lastModified());
        sources.save(source);

        eventsToPublish.forEach(events::publish);

        return new IngestReport(fetched.entries().size(), created, duplicated, rejected, false, null);
    }
}
