package com.noticiasplaneta.media.adapter.out.persistence;

import com.noticiasplaneta.media.application.port.out.MediaRepository;
import com.noticiasplaneta.media.domain.MediaAsset;
import com.noticiasplaneta.media.domain.MediaAssetId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
class MediaRepositoryAdapter implements MediaRepository {

    private final SpringDataMediaRepository jpa;

    MediaRepositoryAdapter(SpringDataMediaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public MediaAsset save(MediaAsset asset) {
        return toDomain(jpa.save(toEntity(asset)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MediaAsset> findBySha256(String sha256) {
        return jpa.findBySha256(sha256).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MediaAsset> findById(MediaAssetId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    private MediaAssetJpaEntity toEntity(MediaAsset a) {
        MediaAssetJpaEntity e = new MediaAssetJpaEntity();
        e.id = a.id().value();
        e.sha256 = a.sha256();
        e.storageKey = a.storageKey();
        e.mime = a.mime();
        e.width = a.width();
        e.height = a.height();
        e.sizeBytes = a.sizeBytes();
        e.uploaderId = a.uploaderId();
        e.createdAt = a.createdAt();
        e.altText = a.altText();
        e.credit = a.credit();
        return e;
    }

    private MediaAsset toDomain(MediaAssetJpaEntity e) {
        return MediaAsset.rehydrate(new MediaAssetId(e.id), e.sha256, e.storageKey, e.mime,
                e.width, e.height, e.sizeBytes, e.uploaderId, e.createdAt, e.altText, e.credit);
    }
}
