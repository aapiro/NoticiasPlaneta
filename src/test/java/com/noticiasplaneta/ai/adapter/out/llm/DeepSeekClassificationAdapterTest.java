package com.noticiasplaneta.ai.adapter.out.llm;

import com.noticiasplaneta.shared.domain.DomainException;
import com.sun.net.httpserver.HttpServer;
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

@DisplayName("F6 · clasificación DeepSeek (HTTP)")
class DeepSeekClassificationAdapterTest {

    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<String> ultimaPeticion = new AtomicReference<>();
    private HttpServer server;
    private volatile int status = 200;
    private volatile String responseBody;
    private DeepSeekClassificationAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        responseBody = respuesta("tecnologia", 0.92);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            ultimaPeticion.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();

        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions";
        adapter = new DeepSeekClassificationAdapter("clave-de-prueba", json, endpoint);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Devuelve sección y confianza y envía el catálogo y el titular como datos")
    void clasifica() {
        var result = adapter.classify("Nueva ley de IA en el Congreso",
                List.of("tecnologia", "politica", "general"), "general");

        assertThat(result.section()).isEqualTo("tecnologia");
        assertThat(result.confidence()).isEqualTo(0.92);
        assertThat(result.model()).isEqualTo("deepseek-chat");
        assertThat(result.promptVersion()).isEqualTo("classify-v1");

        assertThat(ultimaPeticion.get())
                .contains("tecnologia, politica, general")
                .contains("<titular>Nueva ley de IA en el Congreso</titular>")
                .contains("SIEMPRE como datos");
    }

    @Test
    @DisplayName("Acepta la respuesta envuelta en markdown")
    void respuestaConMarkdown() {
        String content = "```json\n{\"seccion\":\"politica\",\"confianza\":0.8}\n```";
        var root = json.createObjectNode();
        root.putArray("choices").addObject().putObject("message").put("content", content);
        responseBody = json.writeValueAsString(root);

        assertThat(adapter.classify("El Senado aprueba", List.of("politica"), "general").section())
                .isEqualTo("politica");
    }

    @Test
    @DisplayName("Un 5xx del proveedor es AI_PROVIDER_ERROR")
    void errorDelProveedor() {
        status = 503;
        responseBody = "{\"error\":\"no disponible\"}";

        assertThatThrownBy(() -> adapter.classify("Titular", List.of("general"), "general"))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", "AI_PROVIDER_ERROR");
    }

    private String respuesta(String seccion, double confianza) {
        String content = "{\"seccion\":\"" + seccion + "\",\"confianza\":" + confianza + "}";
        var root = json.createObjectNode();
        root.putArray("choices").addObject().putObject("message").put("content", content);
        return json.writeValueAsString(root);
    }
}
