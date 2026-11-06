package com.noticiasplaneta.ingest.application.port.out;

public interface FeedFetcher {

    FeedFetchOutcome fetch(String feedUrl, String etag, String lastModified);
}
