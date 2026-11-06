package com.noticiasplaneta.ingest.domain;

import java.util.Objects;
import java.util.UUID;

public record SourceId(UUID value) {

    public SourceId {
        Objects.requireNonNull(value, "value");
    }

    public static SourceId newId() {
        return new SourceId(UUID.randomUUID());
    }

    public static SourceId of(String raw) {
        return new SourceId(UUID.fromString(raw));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
