package com.noticiasplaneta.news.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** F06: outbox transaccional para no perder los efectos de una publicación. */
public interface OutboxPort {

    void enqueue(OutboxEvent event);

    List<OutboxEvent> findPending(int limit);

    void markProcessed(UUID id, Instant now);

    void markFailed(UUID id, String error);
}
