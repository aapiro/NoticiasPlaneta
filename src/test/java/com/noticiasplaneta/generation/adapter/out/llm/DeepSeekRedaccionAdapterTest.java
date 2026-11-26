package com.noticiasplaneta.generation.adapter.out.llm;

import com.noticiasplaneta.generation.application.port.out.RedaccionPort;
import com.noticiasplaneta.shared.domain.DomainException;
import com.noticiasplaneta.shared.observability.LangfuseProperties;
import com.noticiasplaneta.shared.observability.LangfuseTracer;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato del adaptador de redacción contra un proveedor falso: no hay clave de DeepSeek
 * en esta máquina, así que lo que se prueba es el contrato (prompt, parseo, coste y error),
 * no la calidad del modelo.
 */
@DisplayName("F8 · DeepSeek: contrato de redacción")
class DeepSeekRedaccionAdapterTest {

    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> peticion = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String respuesta;
    private DeepSeekRedaccionAdapter adaptador;

    @BeforeEach
    void setUp() throws IOException {
        respuesta = respuestaConBorrador();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            peticion.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();

        SdkTracerProvider provider = SdkTracerProvider.builder().build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build();
        LangfuseTracer tracer = new LangfuseTracer(sdk, new LangfuseProperties(
                false, "https://cloud.langfuse.com", "", "", 1.0,
                "noticiasplaneta", "test", 10_000));
        adaptador = new DeepSeekRedaccionAdapter("clave-de-prueba", json, tracer,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Devuelve titular y párrafos con sus referencias, y calcula el coste")
    void parseaTitularParrafosYCoste() {
        RedaccionPort.Resultado resultado = adaptador.redactar(peticion());

        assertThat(resultado.borrador().titular()).isEqualTo("La sequía deja Sau al 18 %");
        assertThat(resultado.borrador().parrafos()).hasSize(2);
        assertThat(resultado.borrador().parrafos().get(0).citas()).containsExactly(1);
        assertThat(resultado.borrador().parrafos().get(1).citas()).containsExactly(2);
        assertThat(resultado.promptVersion()).isEqualTo("generar-v1");
        assertThat(resultado.proveedor()).isEqualTo("deepseek");
        // 1.000 tokens de entrada (0,30 USD/M) + 500 de salida (1,20 USD/M) = 900 µUSD
        assertThat(resultado.costeMicros()).isEqualTo(900);
    }

    @Test
    @DisplayName("El material viaja numerado, con medio, URL y fecha, y el enfoque de la sección")
    void enviaMaterialNumeradoYEnfoque() {
        adaptador.redactar(peticion());

        String cuerpo = peticion.get();
        assertThat(cuerpo).contains("[1]").contains("[2]")
                .contains("Diario A").contains("https://a.example/sequia")
                .contains("2026-11-25T08:00:00Z")
                .contains("IZQUIERDA")
                .contains("derechos sociales");
    }

    @Test
    @DisplayName("Un error del proveedor se convierte en AI_PROVIDER_ERROR")
    void errorDelProveedor() {
        status = 500;

        assertThatThrownBy(() -> adaptador.redactar(peticion()))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo("AI_PROVIDER_ERROR"));
    }

    @Test
    @DisplayName("Una respuesta vacía no se convierte en un borrador vacío silencioso")
    void respuestaVacia() {
        respuesta = "{\"choices\":[{\"message\":{\"content\":\"\"}}],\"usage\":{\"prompt_tokens\":1}}";

        assertThatThrownBy(() -> adaptador.redactar(peticion()))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo("AI_PROVIDER_ERROR"));
    }

    private RedaccionPort.Peticion peticion() {
        return new RedaccionPort.Peticion("sequía embalses", "economia", "IZQUIERDA",
                "Línea editorial de izquierda: impacto en derechos sociales. No inventes.",
                List.of(
                        new RedaccionPort.Fuente(1, "Diario A", "https://a.example/sequia",
                                "2026-11-25T08:00:00Z", "Sau al 18 %",
                                "La capacidad del embalse de Sau ha bajado al 18 % este mes."),
                        new RedaccionPort.Fuente(2, "Diario B", "https://b.example/govern",
                                "2026-11-25T09:00:00Z", "Restricciones",
                                "El Govern descartó cortes de agua, con las reservas al 19 %.")));
    }

    /** Respuesta de la API con el borrador del modelo dentro de "content" (como llega de verdad). */
    private String respuestaConBorrador() {
        String borrador = """
                {"titular":"La sequía deja Sau al 18 %","parrafos":[
                  {"texto":"El embalse de Sau ha bajado al 18 % de su capacidad este mes.","fuentes":[1]},
                  {"texto":"El Govern descartó cortes de agua con las reservas al 19 %.","fuentes":[2]}]}
                """.replace("\n", "");
        var root = json.createObjectNode();
        var choices = root.putArray("choices");
        choices.addObject().putObject("message").put("content", borrador);
        var usage = root.putObject("usage");
        usage.put("prompt_tokens", 1000);
        usage.put("completion_tokens", 500);
        return json.writeValueAsString(root);
    }
}
