package com.noticiasplaneta.shared.observability;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test contra un proyecto real de Langfuse.
 *
 * <p>Se salta si no hay claves. Para ejecutarlo:
 * <pre>
 * set -a; source .env; set +a
 * ./mvnw -q -Dtest=LangfuseLiveSmokeTest test
 * </pre>
 * Imprime {@code LANGFUSE_TRACE_ID=...} para localizar el trace en la UI.
 */
@DisplayName("Langfuse · smoke test real")
@EnabledIfEnvironmentVariable(named = "LANGFUSE_PUBLIC_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LANGFUSE_SECRET_KEY", matches = ".+")
class LangfuseLiveSmokeTest {

    @Test
    void emiteUnTraceReal() {
        String host = System.getenv().getOrDefault("LANGFUSE_BASE_URL",
                System.getenv().getOrDefault("LANGFUSE_HOST", "https://cloud.langfuse.com"));
        LangfuseProperties properties = new LangfuseProperties(
                true, host,
                System.getenv("LANGFUSE_PUBLIC_KEY"),
                System.getenv("LANGFUSE_SECRET_KEY"),
                1.0, "noticiasplaneta", "smoke", 10_000);

        OpenTelemetrySdk sdk = new LangfuseTracingConfiguration().openTelemetry(properties);
        String traceId = null;
        try {
            LangfuseTracer tracer = new LangfuseTracer(sdk, properties);
            try (LangfuseTracer.Trace trace = tracer.startTrace("langfuse-smoke-test",
                    "{\"source\":\"maven\"}", Map.of("purpose", "smoke"), List.of("smoke"))) {
                traceId = trace.traceId();
                try (LangfuseTracer.Generation generation = tracer.startGeneration(
                        "langfuse-smoke-test", "generate-enrichment", "deepseek-flash",
                        "[{\"role\":\"user\",\"content\":\"ping\"}]",
                        Map.of("prompt_version", "enrich-v1"), List.of("smoke"))) {
                    generation.usage(1, 1, 2);
                    generation.output("{\"summary\":\"pong\"}");
                }
                trace.output("{\"summary\":\"pong\"}");
            }

            assertThat(sdk.getSdkTracerProvider().forceFlush()
                    .join(15, TimeUnit.SECONDS).isSuccess())
                    .as("Langfuse debe aceptar el batch OTLP")
                    .isTrue();
            System.out.println("LANGFUSE_TRACE_ID=" + traceId);
        } finally {
            sdk.close();
        }
    }
}
