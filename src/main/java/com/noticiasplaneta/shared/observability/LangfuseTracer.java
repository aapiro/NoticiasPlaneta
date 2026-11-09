package com.noticiasplaneta.shared.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Emisor de trazas Langfuse sobre OpenTelemetry.
 *
 * <p>Langfuse recibe OTLP/HTTP y mapea los atributos {@code langfuse.*}. Se crea un
 * span raíz por unidad de trabajo y un {@code generation} hijo por llamada al modelo,
 * con modelo, prompt, tokens, entrada/salida y errores.
 *
 * <p>Si Langfuse no está configurado, todos los métodos son no-op: no hay coste ni
 * riesgo para la aplicación.
 */
@Component
public class LangfuseTracer {

    private static final AttributeKey<List<String>> TRACE_TAGS =
            AttributeKey.stringArrayKey("langfuse.trace.tags");

    private final Tracer tracer;
    private final boolean active;
    private final int maxPayloadChars;
    private final String environment;

    public LangfuseTracer(OpenTelemetry openTelemetry, LangfuseProperties properties) {
        this.tracer = openTelemetry.getTracer("noticiasplaneta");
        this.active = properties.active();
        this.maxPayloadChars = properties.maxPayloadChars();
        this.environment = properties.environment();
    }

    /**
     * Abre un trace (span raíz). El span queda activo para que las observaciones hijas
     * se aniden automáticamente por el contexto de OpenTelemetry.
     */
    public Trace startTrace(String traceName, String input, Map<String, String> metadata,
                            List<String> tags) {
        if (!active) {
            return Trace.NOOP;
        }
        Span span = tracer.spanBuilder(traceName).startSpan();
        applyCommon(span, traceName, metadata, tags);
        span.setAttribute("langfuse.observation.type", "span");
        setJson(span, "langfuse.observation.input", input, maxPayloadChars);
        return new Trace(span, span.makeCurrent(), maxPayloadChars);
    }

    /**
     * Abre una observación de tipo {@code generation} para una llamada al LLM. Debe
     * invocarse dentro de un {@link Trace} para que quede anidada.
     */
    public Generation startGeneration(String traceName, String name, String model, String input,
                                      Map<String, String> metadata, List<String> tags) {
        if (!active) {
            return Generation.NOOP;
        }
        Span span = tracer.spanBuilder(name).startSpan();
        applyCommon(span, traceName, metadata, tags);
        span.setAttribute("langfuse.observation.type", "generation");
        span.setAttribute("langfuse.observation.model.name", model);
        span.setAttribute("gen_ai.request.model", model);
        span.setAttribute("gen_ai.operation.name", "chat");
        span.setAttribute("gen_ai.system", "deepseek");
        setJson(span, "langfuse.observation.input", input, maxPayloadChars);
        return new Generation(span, span.makeCurrent(), maxPayloadChars);
    }

    private void applyCommon(Span span, String traceName, Map<String, String> metadata,
                             List<String> tags) {
        span.setAttribute("langfuse.trace.name", traceName);
        span.setAttribute("langfuse.environment", environment);
        if (tags != null && !tags.isEmpty()) {
            span.setAttribute(TRACE_TAGS, tags);
        }
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                if (key != null && value != null) {
                    span.setAttribute("langfuse.observation.metadata." + key, value);
                    span.setAttribute("langfuse.trace.metadata." + key, value);
                }
            });
        }
    }

    private static void setJson(Span span, String key, String json, int maxChars) {
        if (span == null || json == null) {
            return;
        }
        String value = (maxChars <= 0 || json.length() <= maxChars)
                ? json
                : json.substring(0, maxChars) + "…[truncado]";
        span.setAttribute(key, value);
    }

    private static void markError(Span span, String message) {
        if (span == null) {
            return;
        }
        String safe = (message == null || message.isBlank()) ? "error" : message;
        span.setStatus(StatusCode.ERROR, safe);
        span.setAttribute("langfuse.observation.level", "ERROR");
        span.setAttribute("langfuse.observation.status_message", safe);
    }

    /** Handle del trace raíz. */
    public static final class Trace implements AutoCloseable {

        private static final Trace NOOP = new Trace(null, null, 0);

        private final Span span;
        private final Scope scope;
        private final int maxChars;

        private Trace(Span span, Scope scope, int maxChars) {
            this.span = span;
            this.scope = scope;
            this.maxChars = maxChars;
        }

        public void output(String json) {
            setJson(span, "langfuse.observation.output", json, maxChars);
        }

        /** ID del trace para poder localizarlo en Langfuse. */
        public String traceId() {
            return span == null ? null : span.getSpanContext().getTraceId();
        }

        public void error(String message) {
            markError(span, message);
        }

        @Override
        public void close() {
            if (scope != null) {
                scope.close();
            }
            if (span != null) {
                span.end();
            }
        }
    }

    /** Handle de una observación {@code generation}. */
    public static final class Generation implements AutoCloseable {

        private static final Generation NOOP = new Generation(null, null, 0);

        private final Span span;
        private final Scope scope;
        private final int maxChars;

        private Generation(Span span, Scope scope, int maxChars) {
            this.span = span;
            this.scope = scope;
            this.maxChars = maxChars;
        }

        public void output(String json) {
            setJson(span, "langfuse.observation.output", json, maxChars);
        }

        public void usage(long inputTokens, long outputTokens) {
            usage(inputTokens, outputTokens, inputTokens + outputTokens);
        }

        public void usage(long inputTokens, long outputTokens, long totalTokens) {
            if (span == null) {
                return;
            }
            span.setAttribute("langfuse.observation.usage_details",
                    "{\"input\":" + inputTokens + ",\"output\":" + outputTokens
                            + ",\"total\":" + totalTokens + "}");
        }

        public void modelParameters(String json) {
            setJson(span, "langfuse.observation.model.parameters", json, maxChars);
        }

        public void prompt(String name, String version) {
            if (span == null) {
                return;
            }
            span.setAttribute("langfuse.observation.prompt.name", name);
            span.setAttribute("langfuse.observation.prompt.version", version);
        }

        public void error(String message) {
            markError(span, message);
        }

        @Override
        public void close() {
            if (scope != null) {
                scope.close();
            }
            if (span != null) {
                span.end();
            }
        }
    }
}
