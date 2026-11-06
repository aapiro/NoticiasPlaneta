package com.noticiasplaneta.news.adapter.out.persistence;

import com.noticiasplaneta.news.domain.ArticleOrigin;
import com.noticiasplaneta.news.domain.ArticleState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "article")
class ArticleJpaEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    String language;

    @Column(name = "section_id", nullable = false)
    String sectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ArticleOrigin origin;

    @Column(name = "ingest_item_id")
    String ingestItemId;

    @Column(nullable = false)
    String slug;

    @Column(nullable = false)
    String title;

    @Column(nullable = false)
    String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ArticleState state;

    @Column(name = "revision_count", nullable = false)
    int revisionCount;

    @Column(name = "published_at")
    Instant publishedAt;

    @Column(name = "scheduled_at")
    Instant scheduledAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "unpublished_at")
    Instant unpublishedAt;

    @Column(name = "unpublish_reason")
    String unpublishReason;

    @Column(name = "correction_note")
    String correctionNote;

    protected ArticleJpaEntity() {
    }
}
