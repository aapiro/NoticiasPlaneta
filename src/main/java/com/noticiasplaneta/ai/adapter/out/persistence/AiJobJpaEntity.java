package com.noticiasplaneta.ai.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_job")
class AiJobJpaEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    String type;

    @Column(name = "article_id", nullable = false)
    UUID articleId;

    @Column(nullable = false)
    String state;

    @Column(nullable = false)
    int attempts;

    @Column(name = "last_error")
    String lastError;

    @Column(name = "cost_micros", nullable = false)
    long costMicros;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected AiJobJpaEntity() {
    }
}
