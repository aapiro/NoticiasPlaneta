package com.noticiasplaneta.media.application.port.out;

import com.noticiasplaneta.media.domain.MediaAsset;
import com.noticiasplaneta.media.domain.MediaAssetId;

import java.util.Optional;

public interface MediaRepository {

    MediaAsset save(MediaAsset asset);

    Optional<MediaAsset> findBySha256(String sha256);

    Optional<MediaAsset> findById(MediaAssetId id);
}
