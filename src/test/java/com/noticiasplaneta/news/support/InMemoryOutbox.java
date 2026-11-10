package com.noticiasplaneta.news.support;

import com.noticiasplaneta.news.application.port.out.OutboxEvent;
import com.noticiasplaneta.news.application.port.out.OutboxPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InMemoryOutbox implements OutboxPort {

    private final Map<UUID, OutboxEvent> pending = new LinkedHashMap<>();
    private final List<OutboxEvent> processed = new ArrayList<>();

    @Override
    public void enqueue(OutboxEvent event) {
        pending.put(event.id(), event);
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return pending.values().stream()
                .sorted(Comparator.comparing(OutboxEvent::createdAt))
                .limit(limit)
                .toList();
    }

    @Override
    public void markProcessed(UUID id, Instant now) {
        OutboxEvent event = pending.remove(id);
        if (event != null) {
            processed.add(event);
        }
    }

    @Override
    public void markFailed(UUID id, String error) {
        // En memoria no hace falta contar intentos para estos tests.
    }

    public List<OutboxEvent> pending() {
        return List.copyOf(pending.values());
    }

    public List<OutboxEvent> processed() {
        return List.copyOf(processed);
    }
}
