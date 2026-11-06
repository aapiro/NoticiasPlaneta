package com.noticiasplaneta.media.application.service;

import com.noticiasplaneta.media.application.port.in.CommitResult;
import com.noticiasplaneta.media.application.port.in.CommitUploadUseCase;
import com.noticiasplaneta.media.application.port.out.ImageProcessorPort;
import com.noticiasplaneta.media.application.port.out.MediaRepository;
import com.noticiasplaneta.media.application.port.out.ObjectStoragePort;
import com.noticiasplaneta.media.domain.AllowedMime;
import com.noticiasplaneta.media.domain.MediaAsset;
import com.noticiasplaneta.shared.domain.DomainException;

import java.time.Clock;
import java.util.Optional;

/**
 * F08 — Paso 2: el servidor relee el objeto, valida el tipo real por magic bytes, reencodea
 * (mata payloads y EXIF) y solo entonces registra el activo. Nunca se confía en el cliente.
 */
public class CommitUploadService implements CommitUploadUseCase {

    private final MediaRepository media;
    private final ObjectStoragePort storage;
    private final ImageProcessorPort images;
    private final Clock clock;

    public CommitUploadService(MediaRepository media, ObjectStoragePort storage,
                               ImageProcessorPort images, Clock clock) {
        this.media = media;
        this.storage = storage;
        this.images = images;
        this.clock = clock;
    }

    @Override
    public CommitResult commit(String storageKey, String uploaderId, String altText, String credit) {
        if (altText == null || altText.trim().length() < MediaAsset.MIN_ALT_LENGTH) {
            storage.delete(storageKey);
            throw new DomainException("ALT_TEXT_REQUIRED",
                    "Una imagen sin texto alternativo no existe para quien no la ve "
                            + "(mínimo " + MediaAsset.MIN_ALT_LENGTH + " caracteres)");
        }

        byte[] raw;
        try {
            raw = storage.get(storageKey);
        } catch (RuntimeException e) {
            throw new DomainException("MEDIA_NOT_FOUND", "No existe el objeto " + storageKey);
        }

        String detected = AllowedMime.detect(raw);
        if (!AllowedMime.isAllowed(detected)) {
            storage.delete(storageKey);
            throw new DomainException("MIME_MISMATCH",
                    "El contenido real no es una imagen permitida: " + detected);
        }

        ImageProcessorPort.ProcessedImage processed = images.process(raw, detected);

        Optional<MediaAsset> existing = media.findBySha256(processed.sha256());
        if (existing.isPresent()) {
            storage.delete(storageKey);
            MediaAsset found = existing.get();
            return new CommitResult(found.id().toString(), found.storageKey(), found.mime(),
                    found.width(), found.height(), found.sizeBytes(), true);
        }

        MediaAsset asset = MediaAsset.register(
                processed.sha256(), processed.mime(), processed.width(), processed.height(),
                processed.bytes().length, uploaderId, clock.instant(), altText, credit);

        storage.put(asset.storageKey(), processed.bytes());
        storage.delete(storageKey);
        MediaAsset saved = media.save(asset);

        return new CommitResult(saved.id().toString(), saved.storageKey(), saved.mime(),
                saved.width(), saved.height(), saved.sizeBytes(), false);
    }
}
