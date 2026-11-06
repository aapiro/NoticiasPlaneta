package com.noticiasplaneta.ingest.support;

import com.noticiasplaneta.shared.domain.EventPublisher;

import java.util.ArrayList;
import java.util.List;

public class InMemoryEventPublisher implements EventPublisher {

    private final List<Object> published = new ArrayList<>();

    @Override
    public void publish(Object event) {
        published.add(event);
    }

    public List<Object> published() {
        return List.copyOf(published);
    }
}
