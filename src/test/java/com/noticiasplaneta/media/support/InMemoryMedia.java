package com.noticiasplaneta.media.support;

import com.noticiasplaneta.media.application.port.out.MediaRepository;
import com.noticiasplaneta.media.domain.MediaAsset;
import com.noticiasplaneta.media.domain.MediaAssetId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryMedia implements MediaRepository {

    private final Map<MediaAssetId, MediaAsset> data = new LinkedHashMap<>();

    @Override
    public MediaAsset save(MediaAsset asset) {
        data.put(asset.id(), asset);
        return asset;
    }

    @Override
    public Optional<MediaAsset> findBySha256(String sha256) {
        return data.values().stream().filter(a -> a.sha256().equals(sha256)).findFirst();
    }

    @Override
    public Optional<MediaAsset> findById(MediaAssetId id) {
        return Optional.ofNullable(data.get(id));
    }

    public long count() {
        return data.size();
    }
}
