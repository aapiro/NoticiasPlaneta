package com.noticiasplaneta.ai.adapter.out.llm;

import com.noticiasplaneta.ai.application.port.out.EnrichmentPort;
import com.noticiasplaneta.shared.observability.LangfuseProperties;
import com.noticiasplaneta.shared.observability.LangfuseTracer;
import com.noticiasplaneta.shared.observability.LangfuseTracingConfiguration;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoría en vivo del camino real: adaptador DeepSeek -> tracer -> Langfuse.
 * Usa un proveedor DeepSeek falso local para no gastar tokens ni necesitar API key.
 *
 * <p>Se salta si no hay claves. Ejecutar con:
 * <pre>
 * set -a; source .env; set +a
 * ./mvnw -q -Dtest=DeepSeekLangfuseLiveTest test
 * </pre>
 */
@DisplayName("DeepSeek · auditoría Langfuse en vivo")
@EnabledIfEnvironmentVariable(named = "LANGFUSE_PUBLIC_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LANGFUSE_SECRET_KEY", matches = ".+")
class DeepSeekLangfuseLiveTest {

    private HttpServer deepSeek;
    private OpenTelemetrySdk sdk;

    @BeforeEach
    void setUp() throws Exception {
        deepSeek = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        deepSeek.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            var json = new ObjectMapper();
            var root = json.createObjectNode();
            var content = "{\"tags\":[\"auditoria\"],"
                    + "\"entities\":[{\"name\":\"Aranda de Duero\",\"type\":\"PLACE\"}],"
                    + "\"summary\":\"Resumen de auditoría.\"}";
            root.putArray("choices").addObject().putObject("message").put("content", content);
            var usage = root.putObject("usage");
            usage.put("prompt_tokens", 21);
            usage.put("completion_tokens", 34);
            usage.put("total_tokens", 55);
            byte[] out = json.writeValueAsString(root).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (var os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        deepSeek.start();
    }

    @AfterEach
    void tearDown() {
        if (sdk != null) {
            sdk.close();
        }
        if (deepSeek != null) {
            deepSeek.stop(0);
        }
    }

    @Test
    @DisplayName("Emite el trace 'enrich-article' del adaptador real")
    void enviaElTraceRealDelEnriquecido() {
        String host = System.getenv().getOrDefault("LANGFUSE_BASE_URL",
                System.getenv().getOrDefault("LANGFUSE_HOST", "https://cloud.langfuse.com"));
        LangfuseProperties properties = new LangfuseProperties(
                true, host,
                System.getenv("LANGFUSE_PUBLIC_KEY"),
                System.getenv("LANGFUSE_SECRET_KEY"),
                1.0, "noticiasplaneta", "live-audit", 30_000);

        sdk = new LangfuseTracingConfiguration().openTelemetry(properties);
        LangfuseTracer tracer = new LangfuseTracer(sdk, properties);
        String endpoint = "http://127.0.0.1:" + deepSeek.getAddress().getPort() + "/chat/completions";
        DeepSeekEnrichmentAdapter adapter = new DeepSeekEnrichmentAdapter(
                "test-key", new ObjectMapper(), tracer, endpoint);

        EnrichmentPort.EnrichmentResult result = adapter.enrich(
                "Auditoría de tracing", "Cuerpo de la noticia de auditoría.");

        assertThat(result.summary()).isEqualTo("Resumen de auditoría.");
        assertThat(sdk.getSdkTracerProvider().forceFlush()
                .join(15, TimeUnit.SECONDS).isSuccess())
                .as("Langfuse debe aceptar el trace del adaptador")
                .isTrue();
    }
}
