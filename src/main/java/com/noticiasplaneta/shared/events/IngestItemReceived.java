package com.noticiasplaneta.shared.events;

import java.time.Instant;
import java.util.UUID;

public record IngestItemReceived(
        UUID itemId,
        UUID sourceId,
        String canonicalUrl,
        Instant publishedAt,
        String contentHash) {

    public static final String TYPE = "IngestItemReceived.v1";
}
