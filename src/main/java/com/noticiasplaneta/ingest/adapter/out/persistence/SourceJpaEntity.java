package com.noticiasplaneta.ingest.adapter.out.persistence;

import com.noticiasplaneta.ingest.domain.SourceState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source")
class SourceJpaEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    String name;

    @Column(name = "feed_url", nullable = false)
    String feedUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    SourceState state;

    @Column(name = "failure_count", nullable = false)
    int failureCount;

    @Column(name = "last_checked_at")
    Instant lastCheckedAt;

    String etag;

    @Column(name = "last_modified")
    String lastModified;

    protected SourceJpaEntity() {
    }
}
