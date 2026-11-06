package com.noticiasplaneta.ingest.support;

import com.noticiasplaneta.ingest.application.port.out.FeedFetchOutcome;
import com.noticiasplaneta.ingest.application.port.out.FeedFetcher;

public class StubFeedFetcher implements FeedFetcher {

    private FeedFetchOutcome outcome = new FeedFetchOutcome.Fetched(java.util.List.of(), null, null);

    public void willReturn(FeedFetchOutcome outcome) {
        this.outcome = outcome;
    }

    @Override
    public FeedFetchOutcome fetch(String feedUrl, String etag, String lastModified) {
        return outcome;
    }
}
