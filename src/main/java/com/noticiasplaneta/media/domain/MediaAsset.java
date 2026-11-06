package com.noticiasplaneta.media.domain;

import com.noticiasplaneta.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * F08 — Activo de medios.
 *
 * <p>RN4: la clave es content-addressed (`media/{sha256[0:2]}/{sha256}.{ext}`), lo que hace
 * el almacenamiento inmutable y la deduplicación gratis. RN5: `altText` obligatorio.
 */
public final class MediaAsset {

    private final MediaAssetId id;
    private final String sha256;
    private final String storageKey;
    private final String mime;
    private final int width;
    private final int height;
    private final long sizeBytes;
    private final String uploaderId;
    private final Instant createdAt;
    private String altText;
    private String credit;

    private MediaAsset(MediaAssetId id, String sha256, String storageKey, String mime, int width,
                       int height, long sizeBytes, String uploaderId, Instant createdAt,
                       String altText, String credit) {
        this.id = id;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.mime = mime;
        this.width = width;
        this.height = height;
        this.sizeBytes = sizeBytes;
        this.uploaderId = uploaderId;
        this.createdAt = createdAt;
        this.altText = altText;
        this.credit = credit;
    }

    public static MediaAsset register(String sha256, String mime, int width, int height,
                                      long sizeBytes, String uploaderId, Instant now) {
        if (sha256 == null || sha256.length() != 64) {
            throw new DomainException("MEDIA_HASH_INVALID", "Huella de contenido inválida");
        }
        if (!AllowedMime.isAllowed(mime)) {
            throw new DomainException("MIME_NOT_ALLOWED", "Tipo no permitido: " + mime);
        }
        if (width <= 0 || height <= 0) {
            throw new DomainException("MEDIA_DIMENSIONS_INVALID", "Dimensiones inválidas");
        }
        if (sizeBytes <= 0 || sizeBytes > AllowedMime.MAX_SIZE_BYTES) {
            throw new DomainException("MEDIA_SIZE_INVALID", "Tamaño fuera de rango");
        }
        String key = storageKeyFor(sha256, mime);
        return new MediaAsset(MediaAssetId.newId(), sha256, key, mime, width, height,
                sizeBytes, Objects.requireNonNull(uploaderId, "uploaderId"), now, null, null);
    }

    public static MediaAsset rehydrate(MediaAssetId id, String sha256, String storageKey, String mime,
                                       int width, int height, long sizeBytes, String uploaderId,
                                       Instant createdAt, String altText, String credit) {
        return new MediaAsset(id, sha256, storageKey, mime, width, height, sizeBytes,
                uploaderId, createdAt, altText, credit);
    }

    public static String storageKeyFor(String sha256, String mime) {
        return "media/%s/%s.%s".formatted(sha256.substring(0, 2), sha256, AllowedMime.extensionFor(mime));
    }

    public void describe(String altText, String credit) {
        if (altText == null || altText.isBlank()) {
            throw new DomainException("ALT_REQUIRED", "El texto alternativo es obligatorio");
        }
        this.altText = altText.trim();
        this.credit = credit == null ? null : credit.trim();
    }

    public void assertPublishable() {
        if (altText == null || altText.isBlank()) {
            throw new DomainException("ALT_REQUIRED",
                    "No se puede publicar un artículo con imágenes sin texto alternativo");
        }
    }

    public MediaAssetId id() { return id; }
    public String sha256() { return sha256; }
    public String storageKey() { return storageKey; }
    public String mime() { return mime; }
    public int width() { return width; }
    public int height() { return height; }
    public long sizeBytes() { return sizeBytes; }
    public String uploaderId() { return uploaderId; }
    public Instant createdAt() { return createdAt; }
    public String altText() { return altText; }
    public String credit() { return credit; }
}
