package com.noticiasplaneta.shared.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Langfuse · trazas OTel")
class LangfuseTracerTest {

    private InMemorySpanExporter exporter;
    private LangfuseTracer tracer;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build();
        tracer = new LangfuseTracer(sdk, activeProperties());
    }

    @Test
    @DisplayName("Crea un trace con una generation anidada y los atributos que mapea Langfuse")
    void creaTraceYGeneration() {
        try (LangfuseTracer.Trace trace = tracer.startTrace("enrich-article",
                "{\"article_title\":\"Titular\"}",
                Map.of("provider", "deepseek"), List.of("ai", "enrichment"))) {
            try (LangfuseTracer.Generation generation = tracer.startGeneration(
                    "enrich-article", "generate-enrichment", "deepseek-flash",
                    "[{\"role\":\"user\",\"content\":\"hola\"}]",
                    Map.of("prompt_version", "enrich-v1"), List.of("ai", "enrichment"))) {
                generation.prompt("article-enrichment", "enrich-v1");
                generation.usage(12, 34, 46);
                generation.output("{\"summary\":\"ok\"}");
            }
            trace.output("{\"summary\":\"ok\"}");
        }

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(2);

        SpanData root = spans.stream()
                .filter(s -> s.getName().equals("enrich-article"))
                .findFirst().orElseThrow();
        SpanData generation = spans.stream()
                .filter(s -> s.getName().equals("generate-enrichment"))
                .findFirst().orElseThrow();

        assertThat(generation.getParentSpanId()).isEqualTo(root.getSpanId());
        assertThat(generation.getTraceId()).isEqualTo(root.getTraceId());

        assertThat(root.getAttributes().get(AttributeKey.stringKey("langfuse.observation.type")))
                .isEqualTo("span");
        assertThat(generation.getAttributes().get(AttributeKey.stringKey("langfuse.observation.type")))
                .isEqualTo("generation");
        assertThat(generation.getAttributes().get(AttributeKey.stringKey("langfuse.observation.model.name")))
                .isEqualTo("deepseek-flash");
        assertThat(generation.getAttributes().get(AttributeKey.stringKey("langfuse.observation.usage_details")))
                .isEqualTo("{\"input\":12,\"output\":34,\"total\":46}");
        assertThat(generation.getAttributes().get(AttributeKey.stringKey("langfuse.observation.prompt.name")))
                .isEqualTo("article-enrichment");
        assertThat(generation.getAttributes().get(AttributeKey.stringKey("langfuse.observation.prompt.version")))
                .isEqualTo("enrich-v1");
        assertThat(generation.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output")))
                .contains("summary");
        assertThat(root.getAttributes().get(AttributeKey.stringKey("langfuse.trace.name")))
                .isEqualTo("enrich-article");
        assertThat(root.getAttributes().get(AttributeKey.stringArrayKey("langfuse.trace.tags")))
                .containsExactly("ai", "enrichment");
    }

    @Test
    @DisplayName("Sin claves es no-op y no emite nada")
    void sinClavesEsNoop() {
        LangfuseProperties sinClaves = new LangfuseProperties(true, "https://cloud.langfuse.com",
                "", "", 1.0, "noticiasplaneta", "test", 10_000);
        LangfuseTracer noop = new LangfuseTracer(
                OpenTelemetrySdk.builder().setTracerProvider(SdkTracerProvider.builder().build()).build(),
                sinClaves);

        try (LangfuseTracer.Trace trace = noop.startTrace("x", "{}", Map.of(), List.of())) {
            try (LangfuseTracer.Generation generation = noop.startGeneration(
                    "x", "y", "m", "[]", Map.of(), List.of())) {
                generation.usage(1, 2);
                generation.output("{}");
            }
            trace.output("{}");
        }

        assertThat(exporter.getFinishedSpanItems()).isEmpty();
    }

    private static LangfuseProperties activeProperties() {
        return new LangfuseProperties(true, "https://cloud.langfuse.com",
                "pk-lf-test", "sk-lf-test", 1.0, "noticiasplaneta", "test", 10_000);
    }
}
