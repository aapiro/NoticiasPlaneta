package com.noticiasplaneta.media.domain;

import java.util.Objects;
import java.util.UUID;

public record MediaAssetId(UUID value) {

    public MediaAssetId {
        Objects.requireNonNull(value, "value");
    }

    public static MediaAssetId newId() {
        return new MediaAssetId(UUID.randomUUID());
    }

    public static MediaAssetId of(String raw) {
        return new MediaAssetId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
