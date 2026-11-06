package com.noticiasplaneta.ingest.adapter.out.persistence;

import com.noticiasplaneta.ingest.application.port.out.IngestItemRepository;
import com.noticiasplaneta.ingest.domain.ContentHash;
import com.noticiasplaneta.ingest.domain.IngestItem;
import com.noticiasplaneta.ingest.domain.IngestItemId;
import com.noticiasplaneta.ingest.domain.SourceId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
class IngestItemRepositoryAdapter implements IngestItemRepository {

    private final SpringDataIngestItemRepository jpa;

    IngestItemRepositoryAdapter(SpringDataIngestItemRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public IngestItem save(IngestItem item) {
        jpa.save(toEntity(item));
        return item;
    }

    @Override
    @Transactional
    public void saveAll(List<IngestItem> items) {
        jpa.saveAll(items.stream().map(this::toEntity).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySourceAndExternalId(SourceId sourceId, String externalId) {
        return jpa.existsBySourceIdAndExternalId(sourceId.value(), externalId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySourceAndContentHash(SourceId sourceId, ContentHash contentHash) {
        return jpa.existsBySourceIdAndContentHash(sourceId.value(), contentHash.value());
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<com.noticiasplaneta.ingest.domain.IngestItem> findById(
            com.noticiasplaneta.ingest.domain.IngestItemId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpa.count();
    }

    private IngestItemJpaEntity toEntity(IngestItem i) {
        IngestItemJpaEntity e = new IngestItemJpaEntity();
        e.id = i.id().value();
        e.sourceId = i.sourceId().value();
        e.externalId = i.externalId();
        e.contentHash = i.contentHash().value();
        e.title = i.title();
        e.body = i.body();
        e.canonicalUrl = i.canonicalUrl();
        e.publishedAt = i.publishedAt();
        e.author = i.author();
        return e;
    }

    /** Reconstruye el item desde persistencia (usado por tests de integración). */
    IngestItem toDomain(IngestItemJpaEntity e) {
        return IngestItem.receive(new SourceId(e.sourceId),
                new com.noticiasplaneta.ingest.domain.FeedEntry(
                        e.externalId, e.title, e.body, e.canonicalUrl, e.publishedAt, e.author));
    }

    IngestItemId idOf(IngestItemJpaEntity e) {
        return new IngestItemId(e.id);
    }
}
