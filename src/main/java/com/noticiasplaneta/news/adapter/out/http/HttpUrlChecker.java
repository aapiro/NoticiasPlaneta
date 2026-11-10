package com.noticiasplaneta.news.adapter.out.http;

import com.noticiasplaneta.news.application.port.out.UrlChecker;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;

/** Comprobación HTTP con timeout corto; si falla, la fuente queda unreachable. */
@Component
class HttpUrlChecker implements UrlChecker {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client;
    private final Clock clock;

    HttpUrlChecker(Clock clock) {
        this.clock = clock;
        this.client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public Check check(String url) {
        try {
            HttpRequest head = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            int status = client.send(head, HttpResponse.BodyHandlers.discarding()).statusCode();

            if (status == 405 || status == 501) {
                HttpRequest get = HttpRequest.newBuilder(URI.create(url))
                        .timeout(TIMEOUT)
                        .GET()
                        .build();
                status = client.send(get, HttpResponse.BodyHandlers.discarding()).statusCode();
            }

            return new Check(status, status >= 200 && status < 400, clock.instant());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Check(0, false, clock.instant());
        } catch (Exception e) {
            return new Check(0, false, clock.instant());
        }
    }
}
