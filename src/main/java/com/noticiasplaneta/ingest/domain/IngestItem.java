package com.noticiasplaneta.ingest.domain;

import com.noticiasplaneta.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * Elemento de ingesta: un item de un feed convertido a algo que el sistema puede procesar.
 *
 * <p>RN4 (F01): el cuerpo se trunca a {@value #MAX_BODY_LENGTH} caracteres.
 */
public final class IngestItem {

    public static final int MAX_BODY_LENGTH = 100_000;
    public static final int MAX_TITLE_LENGTH = 500;

    private final IngestItemId id;
    private final SourceId sourceId;
    private final String externalId;
    private final ContentHash contentHash;
    private final String title;
    private final String body;
    private final String canonicalUrl;
    private final Instant publishedAt;
    private final String author;

    private IngestItem(IngestItemId id, SourceId sourceId, String externalId, ContentHash contentHash,
                       String title, String body, String canonicalUrl, Instant publishedAt, String author) {
        this.id = id;
        this.sourceId = sourceId;
        this.externalId = externalId;
        this.contentHash = contentHash;
        this.title = title;
        this.body = body;
        this.canonicalUrl = canonicalUrl;
        this.publishedAt = publishedAt;
        this.author = author;
    }

    public static IngestItem receive(SourceId sourceId, FeedEntry entry) {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(entry, "entry");

        String title = entry.title() == null ? "" : entry.title().trim();
        String url = entry.canonicalUrl() == null ? "" : entry.canonicalUrl().trim();

        if (title.isBlank()) {
            throw new DomainException("ITEM_TITLE_REQUIRED", "El item no tiene título");
        }
        if (url.isBlank()) {
            throw new DomainException("ITEM_URL_REQUIRED", "El item no tiene enlace canónico");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH);
        }

        String body = entry.body() == null ? "" : entry.body().trim();
        if (body.length() > MAX_BODY_LENGTH) {
            body = body.substring(0, MAX_BODY_LENGTH);
        }

        String externalId = entry.externalId() == null || entry.externalId().isBlank()
                ? url
                : entry.externalId().trim();

        return new IngestItem(
                IngestItemId.newId(),
                sourceId,
                externalId,
                ContentHash.of(title, body),
                title,
                body,
                url,
                entry.publishedAt(),
                entry.author());
    }

    public IngestItemId id() { return id; }
    public SourceId sourceId() { return sourceId; }
    public String externalId() { return externalId; }
    public ContentHash contentHash() { return contentHash; }
    public String title() { return title; }
    public String body() { return body; }
    public String canonicalUrl() { return canonicalUrl; }
    public Instant publishedAt() { return publishedAt; }
    public String author() { return author; }
}
