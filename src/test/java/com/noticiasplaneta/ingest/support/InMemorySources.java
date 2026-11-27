package com.noticiasplaneta.ingest.support;

import com.noticiasplaneta.ingest.application.port.out.SourceRepository;
import com.noticiasplaneta.ingest.domain.Source;
import com.noticiasplaneta.ingest.domain.SourceId;
import com.noticiasplaneta.ingest.domain.SourceState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemorySources implements SourceRepository {

    private final Map<SourceId, Source> data = new LinkedHashMap<>();

    @Override
    public Source save(Source source) {
        data.put(source.id(), source);
        return source;
    }

    @Override
    public Optional<Source> findById(SourceId id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public Optional<Source> findByFeedUrl(String feedUrl) {
        return data.values().stream()
                .filter(s -> s.feedUrl().equals(feedUrl))
                .findFirst();
    }

    @Override
    public List<Source> findByState(SourceState state) {
        return data.values().stream().filter(s -> s.state() == state).toList();
    }

    @Override
    public void delete(SourceId id) {
        data.remove(id);
    }

    /** En memoria no hay piezas: el recuento se simula desde el test con {@link #setItems}. */
    private final Map<SourceId, Long> piezas = new LinkedHashMap<>();

    public void setItems(SourceId id, long cuantas) {
        piezas.put(id, cuantas);
    }

    @Override
    public long countItems(SourceId id) {
        return piezas.getOrDefault(id, 0L);
    }

    public List<Source> all() {
        return new ArrayList<>(data.values());
    }
}
