package com.noticiasplaneta.ingest.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataIngestItemRepository extends JpaRepository<IngestItemJpaEntity, UUID> {

    boolean existsBySourceIdAndExternalId(UUID sourceId, String externalId);

    boolean existsBySourceIdAndContentHash(UUID sourceId, String contentHash);
}
