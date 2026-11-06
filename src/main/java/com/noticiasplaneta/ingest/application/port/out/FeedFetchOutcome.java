package com.noticiasplaneta.ingest.application.port.out;

import com.noticiasplaneta.ingest.domain.FeedEntry;

import java.util.List;

/** Resultado de intentar leer un feed. */
public sealed interface FeedFetchOutcome {

    /** E01: el servidor responde 304, no hay nada nuevo. */
    record NotModified() implements FeedFetchOutcome {}

    record Fetched(List<FeedEntry> entries, String etag, String lastModified) implements FeedFetchOutcome {}

    /** E02/E03: timeout, 5xx o feed ilegible. */
    record Failed(String reason) implements FeedFetchOutcome {}
}
