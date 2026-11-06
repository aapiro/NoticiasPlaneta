package com.noticiasplaneta.ingest.application.port.out;

import com.noticiasplaneta.ingest.domain.ContentHash;
import com.noticiasplaneta.ingest.domain.IngestItem;
import com.noticiasplaneta.ingest.domain.SourceId;

import java.util.List;

public interface IngestItemRepository {

    IngestItem save(IngestItem item);

    void saveAll(List<IngestItem> items);

    boolean existsBySourceAndExternalId(SourceId sourceId, String externalId);

    boolean existsBySourceAndContentHash(SourceId sourceId, ContentHash contentHash);

    long count();
}
