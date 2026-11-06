package com.noticiasplaneta.media.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_asset")
class MediaAssetJpaEntity {

    @Id
    UUID id;

    @Column(nullable = false, unique = true)
    String sha256;

    @Column(name = "storage_key", nullable = false)
    String storageKey;

    @Column(nullable = false)
    String mime;

    @Column(nullable = false)
    int width;

    @Column(nullable = false)
    int height;

    @Column(name = "size_bytes", nullable = false)
    long sizeBytes;

    @Column(name = "uploader_id", nullable = false)
    String uploaderId;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "alt_text")
    String altText;

    String credit;

    protected MediaAssetJpaEntity() {
    }
}
