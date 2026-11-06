package com.noticiasplaneta.ingest.application.port.in;

import java.util.UUID;

public record IngestItemView(UUID id, UUID sourceId, String title, String body,
                             String canonicalUrl, String author) {
}
