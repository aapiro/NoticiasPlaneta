package com.noticiasplaneta.ai.adapter.out.llm;

import com.noticiasplaneta.ai.application.port.out.EnrichmentPort;
import com.noticiasplaneta.shared.domain.DomainException;
import com.noticiasplaneta.shared.observability.LangfuseProperties;
import com.noticiasplaneta.shared.observability.LangfuseTracer;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DeepSeek · trazas Langfuse")
class DeepSeekEnrichmentAdapterTest {

    private final ObjectMapper json = new ObjectMapper();
    private InMemorySpanExporter exporter;
    private HttpServer server;
    private volatile int status = 200;
    private volatile String responseBody;
    private DeepSeekEnrichmentAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build();
        LangfuseTracer tracer = new LangfuseTracer(sdk, new LangfuseProperties(
                true, "https://cloud.langfuse.com", "pk-lf-test", "sk-lf-test",
                1.0, "noticiasplaneta", "test", 10_000));

        responseBody = successBody();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();

        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions";
        adapter = new DeepSeekEnrichmentAdapter("test-key", json, tracer, endpoint);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Registra modelo, prompt, tokens y salida en la generation")
    void trazaLaLlamadaAlModelo() {
        EnrichmentPort.EnrichmentResult result = adapter.enrich("Titular", "Cuerpo de la noticia.");

        assertThat(result.summary()).isEqualTo("Resumen de prueba.");
        assertThat(result.tags()).containsExactly("economia");

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(2);

        SpanData generation = spans.stream()
                .filter(s -> s.getName().equals("generate-enrichment"))
                .findFirst().orElseThrow();
        SpanData root = spans.stream()
                .filter(s -> s.getName().equals("enrich-article"))
                .findFirst().orElseThrow();

        assertThat(generation.getParentSpanId()).isEqualTo(root.getSpanId());
        assertThat(generation.getAttributes().get(AttributeKey.stringKey("langfuse.observation.type")))
                .isEqualTo("generation");
        assertThat(generation.getAttributes().get(AttributeKey.stringKey("langfuse.observation.model.name")))
                .isEqualTo("deepseek-flash");
        assertThat(generation.getAttributes().get(AttributeKey.stringKey("langfuse.observation.input")))
                .contains("\"role\":\"system\"").contains("\"role\":\"user\"");
        assertThat(generation.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output")))
                .contains("Resumen de prueba.");
        assertThat(generation.getAttributes().get(AttributeKey.stringKey("langfuse.observation.usage_details")))
                .isEqualTo("{\"input\":12,\"output\":34,\"total\":46}");
        assertThat(generation.getAttributes().get(AttributeKey.stringKey("langfuse.observation.prompt.version")))
                .isEqualTo("enrich-v1");
        assertThat(generation.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    }

    @Test
    @DisplayName("Un error del proveedor queda marcado como ERROR en la generation")
    void marcaErrorDelProveedor() {
        status = 500;
        responseBody = "{\"error\":\"boom\"}";

        assertThatThrownBy(() -> adapter.enrich("Titular", "Cuerpo de la noticia."))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("DeepSeek respondio 500");

        SpanData generation = exporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("generate-enrichment"))
                .findFirst().orElseThrow();

        assertThat(generation.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(generation.getAttributes().get(AttributeKey.stringKey("langfuse.observation.level")))
                .isEqualTo("ERROR");
        assertThat(generation.getAttributes().get(AttributeKey.stringKey("langfuse.observation.status_message")))
                .contains("500");
    }

    private String successBody() {
        var root = json.createObjectNode();
        var content = "{\"tags\":[\"economia\"],"
                + "\"entities\":[{\"name\":\"Pedro Sánchez\",\"type\":\"PERSON\"}],"
                + "\"summary\":\"Resumen de prueba.\"}";
        root.putArray("choices").addObject().putObject("message").put("content", content);
        var usage = root.putObject("usage");
        usage.put("prompt_tokens", 12);
        usage.put("completion_tokens", 34);
        usage.put("total_tokens", 46);
        return json.writeValueAsString(root);
    }
}
