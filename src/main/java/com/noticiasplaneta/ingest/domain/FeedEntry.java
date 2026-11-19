package com.noticiasplaneta.ingest.domain;

import java.time.Instant;

/** Entrada cruda tal y como llega del feed, ya normalizada por el adaptador. */
public record FeedEntry(
        String externalId,
        String title,
        String body,
        String canonicalUrl,
        Instant publishedAt,
        String author,
        String imageUrl) {

    /** Compatibilidad: una entrada sin foto. */
    public FeedEntry(String externalId, String title, String body, String canonicalUrl,
                     Instant publishedAt, String author) {
        this(externalId, title, body, canonicalUrl, publishedAt, author, null);
    }
}
