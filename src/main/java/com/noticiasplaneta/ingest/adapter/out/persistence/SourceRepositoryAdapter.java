package com.noticiasplaneta.ingest.adapter.out.persistence;

import com.noticiasplaneta.ingest.application.port.out.SourceRepository;
import com.noticiasplaneta.ingest.domain.Source;
import com.noticiasplaneta.ingest.domain.SourceId;
import com.noticiasplaneta.ingest.domain.SourceState;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
class SourceRepositoryAdapter implements SourceRepository {

    private final SpringDataSourceRepository jpa;

    SourceRepositoryAdapter(SpringDataSourceRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Source save(Source source) {
        return toDomain(jpa.save(toEntity(source)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Source> findById(SourceId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Source> findByFeedUrl(String feedUrl) {
        return jpa.findFirstByFeedUrl(feedUrl).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Source> findByState(SourceState state) {
        return jpa.findByState(state).stream().map(this::toDomain).toList();
    }

    private SourceJpaEntity toEntity(Source s) {
        SourceJpaEntity e = new SourceJpaEntity();
        e.id = s.id().value();
        e.name = s.name();
        e.feedUrl = s.feedUrl();
        e.state = s.state();
        e.failureCount = s.failureCount();
        e.lastCheckedAt = s.lastCheckedAt();
        e.etag = s.etag();
        e.lastModified = s.lastModified();
        return e;
    }

    private Source toDomain(SourceJpaEntity e) {
        return Source.rehydrate(
                new SourceId(e.id), e.name, e.feedUrl, e.state,
                e.failureCount, e.lastCheckedAt, e.etag, e.lastModified);
    }
}
