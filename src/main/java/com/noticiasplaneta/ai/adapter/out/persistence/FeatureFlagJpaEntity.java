package com.noticiasplaneta.ai.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "feature_flag")
class FeatureFlagJpaEntity {

    @Id
    @Column(name = "capability", nullable = false)
    String capability;

    @Column(name = "mode", nullable = false)
    String mode;

    @Column(name = "updated_by")
    String updatedBy;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected FeatureFlagJpaEntity() {
    }

    FeatureFlagJpaEntity(String capability, String mode, String updatedBy, Instant updatedAt) {
        this.capability = capability;
        this.mode = mode;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    String capability() {
        return capability;
    }

    String mode() {
        return mode;
    }

    String updatedBy() {
        return updatedBy;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    void update(String mode, String updatedBy, Instant updatedAt) {
        this.mode = mode;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }
}
