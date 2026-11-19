package com.noticiasplaneta.ingest.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface SpringDataIngestItemRepository extends JpaRepository<IngestItemJpaEntity, UUID> {

    boolean existsBySourceIdAndExternalId(UUID sourceId, String externalId);

    boolean existsBySourceIdAndContentHash(UUID sourceId, String contentHash);

    /**
     * Rellena la foto solo si falta: nunca pisa una ya guardada.
     * Los items ingeridos antes de leer Media RSS quedaron con image_url nulo.
     */
    @Modifying
    @Query("""
            update IngestItemJpaEntity i
               set i.imageUrl = :imageUrl
             where i.sourceId = :sourceId
               and i.externalId = :externalId
               and i.imageUrl is null
            """)
    int backfillImage(@Param("sourceId") UUID sourceId,
                      @Param("externalId") String externalId,
                      @Param("imageUrl") String imageUrl);
}
