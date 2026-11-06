package com.noticiasplaneta.media.application.service;

import com.noticiasplaneta.media.application.port.in.PresignResult;
import com.noticiasplaneta.media.application.port.in.PresignUploadUseCase;
import com.noticiasplaneta.media.application.port.out.ObjectStoragePort;
import com.noticiasplaneta.media.domain.AllowedMime;
import com.noticiasplaneta.shared.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * F08 — Paso 1: se firma una URL de subida directa. Nada de proxear 15 MB por la aplicación.
 */
public class PresignUploadService implements PresignUploadUseCase {

    private static final Duration TTL = Duration.ofMinutes(15);

    private final ObjectStoragePort storage;
    private final Clock clock;

    public PresignUploadService(ObjectStoragePort storage, Clock clock) {
        this.storage = storage;
        this.clock = clock;
    }

    @Override
    public PresignResult presign(String filename, String contentType, long sizeBytes, String uploaderId) {
        if (!AllowedMime.isAllowed(contentType)) {
            throw new DomainException("MIME_NOT_ALLOWED", "Tipo no permitido: " + contentType);
        }
        if (sizeBytes <= 0 || sizeBytes > AllowedMime.MAX_SIZE_BYTES) {
            throw new DomainException("MEDIA_SIZE_INVALID",
                    "El tamaño debe estar entre 1 byte y " + AllowedMime.MAX_SIZE_BYTES);
        }

        String key = "tmp/" + UUID.randomUUID();
        String url = storage.presignPut(key, contentType, TTL);
        return new PresignResult(url, key, clock.instant().plus(TTL));
    }
}
