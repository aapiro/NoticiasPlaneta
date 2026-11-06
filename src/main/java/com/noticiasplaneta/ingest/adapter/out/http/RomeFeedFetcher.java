package com.noticiasplaneta.ingest.adapter.out.http;

import com.noticiasplaneta.ingest.application.port.out.FeedFetchOutcome;
import com.noticiasplaneta.ingest.application.port.out.FeedFetcher;
import com.noticiasplaneta.ingest.domain.FeedEntry;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee un feed RSS/Atom por HTTP y lo normaliza a {@link FeedEntry}.
 * Soporta ETag / If-Modified-Since para no descargar lo que no ha cambiado (E01 de F01).
 */
@Component
class RomeFeedFetcher implements FeedFetcher {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client;

    RomeFeedFetcher() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public FeedFetchOutcome fetch(String feedUrl, String etag, String lastModified) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(feedUrl))
                .timeout(TIMEOUT)
                .header("User-Agent", "NoticiasPlaneta/1.0 (+https://noticiasplaneta.com)")
                .GET();

        if (etag != null && !etag.isBlank()) {
            builder.header("If-None-Match", etag);
        }
        if (lastModified != null && !lastModified.isBlank()) {
            builder.header("If-Modified-Since", lastModified);
        }

        try {
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 304) {
                return new FeedFetchOutcome.NotModified();
            }
            if (response.statusCode() >= 400) {
                return new FeedFetchOutcome.Failed("HTTP_" + response.statusCode());
            }

            List<FeedEntry> entries = parse(response.body());
            String newEtag = response.headers().firstValue("ETag").orElse(null);
            String newLastModified = response.headers().firstValue("Last-Modified").orElse(null);
            return new FeedFetchOutcome.Fetched(entries, newEtag, newLastModified);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new FeedFetchOutcome.Failed("INTERRUPTED");
        } catch (java.net.http.HttpTimeoutException e) {
            return new FeedFetchOutcome.Failed("TIMEOUT");
        } catch (Exception e) {
            return new FeedFetchOutcome.Failed("FETCH_ERROR: " + e.getClass().getSimpleName());
        }
    }

    private List<FeedEntry> parse(byte[] body) {
        List<FeedEntry> result = new ArrayList<>();
        try (XmlReader reader = new XmlReader(new ByteArrayInputStream(body))) {
            SyndFeed feed = new SyndFeedInput().build(reader);
            for (SyndEntry entry : feed.getEntries()) {
                String description = entry.getDescription() == null ? null : entry.getDescription().getValue();
                result.add(new FeedEntry(
                        entry.getUri(),
                        entry.getTitle(),
                        description,
                        entry.getLink(),
                        entry.getPublishedDate() == null ? null : entry.getPublishedDate().toInstant(),
                        entry.getAuthor()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("FEED_MALFORMED", e);
        }
        return result;
    }
}
