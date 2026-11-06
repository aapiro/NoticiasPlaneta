package com.noticiasplaneta.ingest.support;

import com.noticiasplaneta.ingest.application.port.out.IngestItemRepository;
import com.noticiasplaneta.ingest.domain.ContentHash;
import com.noticiasplaneta.ingest.domain.IngestItem;
import com.noticiasplaneta.ingest.domain.IngestItemId;
import com.noticiasplaneta.ingest.domain.SourceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class InMemoryItems implements IngestItemRepository {

    private final List<IngestItem> data = new ArrayList<>();

    @Override
    public IngestItem save(IngestItem item) {
        data.add(item);
        return item;
    }

    @Override
    public void saveAll(List<IngestItem> items) {
        data.addAll(items);
    }

    @Override
    public boolean existsBySourceAndExternalId(SourceId sourceId, String externalId) {
        return data.stream().anyMatch(i -> i.sourceId().equals(sourceId) && i.externalId().equals(externalId));
    }

    @Override
    public boolean existsBySourceAndContentHash(SourceId sourceId, ContentHash contentHash) {
        return data.stream().anyMatch(i -> i.sourceId().equals(sourceId) && i.contentHash().equals(contentHash));
    }

    @Override
    public java.util.Optional<IngestItem> findById(IngestItemId id) {
        return data.stream().filter(i -> i.id().equals(id)).findFirst();
    }

    @Override
    public long count() {
        return data.size();
    }

    public List<IngestItem> all() {
        return List.copyOf(data);
    }

    public void seed(IngestItem item) {
        data.add(Objects.requireNonNull(item));
    }
}
