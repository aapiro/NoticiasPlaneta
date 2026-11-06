package com.noticiasplaneta.ai.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "article_enrichment")
class ArticleEnrichmentJpaEntity {

    @Id
    @Column(name = "article_id")
    UUID articleId;

    @Column(nullable = false, columnDefinition = "text[]")
    String[] tags;

    @Column(nullable = false, columnDefinition = "jsonb")
    String entities;

    String summary;

    @Column(nullable = false)
    String model;

    @Column(name = "prompt_version", nullable = false)
    String promptVersion;

    @Column(nullable = false)
    String provider;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected ArticleEnrichmentJpaEntity() {
    }
}
