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

    /**
     * Rellena la foto de un item que ya existía sin ella.
     *
     * <p>Los items ingeridos antes de que se leyera Media RSS tienen {@code image_url}
     * nulo; al reingerir, el feed trae la foto y aquí se aprovecha sin duplicar el item.
     *
     * @return {@code true} si se actualizó alguna fila
     */
    boolean backfillImage(SourceId sourceId, String externalId, String imageUrl);

    java.util.Optional<com.noticiasplaneta.ingest.domain.IngestItem> findById(
            com.noticiasplaneta.ingest.domain.IngestItemId id);
}
