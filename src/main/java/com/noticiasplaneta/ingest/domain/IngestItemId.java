package com.noticiasplaneta.ingest.domain;

import java.util.Objects;
import java.util.UUID;

public record IngestItemId(UUID value) {

    public IngestItemId {
        Objects.requireNonNull(value, "value");
    }

    public static IngestItemId newId() {
        return new IngestItemId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
