package com.noticiasplaneta.ingest.application.event;

import java.time.Instant;
import java.util.UUID;

/** Evento de dominio publicado cuando entra un item nuevo. Versión 1. */
public record IngestItemReceived(
        UUID itemId,
        UUID sourceId,
        String canonicalUrl,
        Instant publishedAt,
        String contentHash) {

    public static final String TYPE = "IngestItemReceived.v1";
}
