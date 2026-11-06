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

    public List<Source> all() {
        return new ArrayList<>(data.values());
    }
}
