package com.noticiasplaneta.news.adapter.in.scheduler;

import com.noticiasplaneta.news.application.port.out.CachePurgePort;
import com.noticiasplaneta.news.application.port.out.OutboxEvent;
import com.noticiasplaneta.news.application.port.out.OutboxPort;
import com.noticiasplaneta.news.application.port.out.SearchIndexPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * F06 — relay idempotente del outbox.
 *
 * <p>Los efectos son at-least-once: si falla la purga o el índice, el evento se reintenta.
 * Los dos adaptadores actuales (caché local y búsqueda SQL) son idempotentes.
 */
@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxPort outbox;
    private final CachePurgePort cache;
    private final SearchIndexPort search;
    private final Clock clock;

    OutboxRelay(OutboxPort outbox, CachePurgePort cache, SearchIndexPort search, Clock clock) {
        this.outbox = outbox;
        this.cache = cache;
        this.search = search;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:10000}", initialDelay = 20000)
    void tick() {
        for (OutboxEvent event : outbox.findPending(20)) {
            try {
                cache.purgeEventually(event.url());
                search.index(event.articleId().toString());
                outbox.markProcessed(event.id(), clock.instant());
            } catch (RuntimeException e) {
                log.warn("Outbox {} falló al propagar: {}", event.id(), e.getMessage());
                outbox.markFailed(event.id(), e.getClass().getSimpleName());
            }
        }
    }
}
