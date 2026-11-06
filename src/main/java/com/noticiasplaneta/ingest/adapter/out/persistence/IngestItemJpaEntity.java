package com.noticiasplaneta.ingest.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingest_item")
class IngestItemJpaEntity {

    @Id
    UUID id;

    @Column(name = "source_id", nullable = false)
    UUID sourceId;

    @Column(name = "external_id", nullable = false)
    String externalId;

    @Column(name = "content_hash", nullable = false)
    String contentHash;

    @Column(nullable = false)
    String title;

    @Column(nullable = false)
    String body;

    @Column(name = "canonical_url", nullable = false)
    String canonicalUrl;

    @Column(name = "published_at")
    Instant publishedAt;

    String author;

    protected IngestItemJpaEntity() {
    }
}
