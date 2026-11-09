package com.noticiasplaneta.shared.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Exporta las trazas OTLP a Langfuse.
 *
 * <p>Langfuse solo acepta OTLP sobre HTTP (JSON/protobuf), no gRPC. La autenticación
 * es HTTP Basic con {@code publicKey:secretKey} en base64 y el header
 * {@code x-langfuse-ingestion-version: 4} para ingesta en tiempo real.
 *
 * <p>Sin claves no se crea exportador: la aplicación arranca igual y no envía nada.
 */
@Configuration
@EnableConfigurationProperties(LangfuseProperties.class)
class LangfuseTracingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LangfuseTracingConfiguration.class);

    @Bean(destroyMethod = "close")
    OpenTelemetrySdk openTelemetry(LangfuseProperties properties) {
        if (!properties.active()) {
            log.info("Langfuse: tracing desactivado (faltan claves o langfuse.enabled=false)");
            return OpenTelemetrySdk.builder()
                    .setTracerProvider(SdkTracerProvider.builder().build())
                    .build();
        }

        String endpoint = properties.host() + "/api/public/otel/v1/traces";
        String auth = Base64.getEncoder().encodeToString(
                (properties.publicKey() + ":" + properties.secretKey())
                        .getBytes(StandardCharsets.UTF_8));

        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint)
                .setTimeout(Duration.ofSeconds(10))
                .addHeader("Authorization", "Basic " + auth)
                .addHeader("x-langfuse-ingestion-version", "4")
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setSampler(Sampler.traceIdRatioBased(properties.sampleRate()))
                .setResource(Resource.getDefault().merge(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), properties.serviceName(),
                        AttributeKey.stringKey("deployment.environment"), properties.environment()))))
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();

        log.info("Langfuse: tracing activo -> {} (servicio={}, muestreo={})",
                endpoint, properties.serviceName(), properties.sampleRate());

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }
}
