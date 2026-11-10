package com.noticiasplaneta.shared.observability;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Langfuse · exportación OTLP/HTTP")
class LangfuseOtlpExportTest {

    @Test
    @DisplayName("Envía al endpoint correcto con Basic Auth y x-langfuse-ingestion-version")
    void exportaConAutenticacionYHeaderDeIngesta() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> ingestionVersion = new AtomicReference<>();
        AtomicInteger bodyBytes = new AtomicInteger();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/public/otel/v1/traces", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            ingestionVersion.set(exchange.getRequestHeaders().getFirst("x-langfuse-ingestion-version"));
            byte[] body = exchange.getRequestBody().readAllBytes();
            bodyBytes.set(body.length);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        try {
            String host = "http://127.0.0.1:" + server.getAddress().getPort();
            LangfuseProperties properties = new LangfuseProperties(
                    true, host, "pk-lf-test", "sk-lf-test",
                    1.0, "noticiasplaneta", "test", 10_000);

            OpenTelemetrySdk sdk = new LangfuseTracingConfiguration().openTelemetry(properties);
            try {
                LangfuseTracer tracer = new LangfuseTracer(sdk, properties);
                try (LangfuseTracer.Trace trace = tracer.startTrace("enrich-article", "{}",
                        Map.of("provider", "deepseek"), List.of("ai"))) {
                    try (LangfuseTracer.Generation generation = tracer.startGeneration(
                            "enrich-article", "generate-enrichment", "deepseek-flash", "[]",
                            Map.of("prompt_version", "enrich-v1"), List.of("ai"))) {
                        generation.usage(1, 2);
                        generation.output("{\"summary\":\"ok\"}");
                    }
                    trace.output("{\"summary\":\"ok\"}");
                }

                sdk.getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS);

                String expectedAuth = "Basic " + Base64.getEncoder().encodeToString(
                        "pk-lf-test:sk-lf-test".getBytes(StandardCharsets.UTF_8));
                assertThat(authorization.get()).isEqualTo(expectedAuth);
                assertThat(ingestionVersion.get()).isEqualTo("4");
                assertThat(bodyBytes.get()).isGreaterThan(0);
            } finally {
                sdk.close();
            }
        } finally {
            server.stop(0);
        }
    }
}
