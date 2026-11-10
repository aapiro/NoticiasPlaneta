package com.noticiasplaneta.news.adapter.out.http;

import com.noticiasplaneta.news.application.port.out.UrlChecker;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("F04 · Comprobación HTTP de fuentes")
class HttpUrlCheckerTest {

    private HttpServer server;
    private HttpUrlChecker checker;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        checker = new HttpUrlChecker(Clock.fixed(Instant.parse("2026-11-10T08:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("200 es alcanzable; 404 no")
    void estados() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        UrlChecker.Check ok = checker.check(base + "/ok");
        assertThat(ok.reachable()).isTrue();
        assertThat(ok.status()).isEqualTo(200);

        UrlChecker.Check missing = checker.check(base + "/missing");
        assertThat(missing.reachable()).isFalse();
        assertThat(missing.status()).isEqualTo(404);
    }
}
