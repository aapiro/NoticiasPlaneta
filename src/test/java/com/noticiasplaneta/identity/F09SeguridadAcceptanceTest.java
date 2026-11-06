package com.noticiasplaneta.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("F09 · Aceptación: seguridad de la API")
class F09SeguridadAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.api-key", () -> "clave-secreta-de-pruebas");
        registry.add("security.agent-id", () -> "newsroom-ingesta");
    }

    @Autowired
    MockMvc mvc;

    private static final String BODY = """
            {"name":"BOE","feedUrl":"https://ejemplo.es/rss"}
            """;

    @Test
    @DisplayName("Escenario: sin clave de agente la API responde 401")
    void sinClave() throws Exception {
        mvc.perform(post("/api/v1/sources").contentType(APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Escenario: con clave incorrecta responde 401")
    void claveIncorrecta() throws Exception {
        mvc.perform(post("/api/v1/sources")
                        .header("X-Api-Key", "clave-equivocada")
                        .contentType(APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Escenario: con la clave correcta la operación pasa")
    void claveCorrecta() throws Exception {
        mvc.perform(post("/api/v1/sources")
                        .header("X-Api-Key", "clave-secreta-de-pruebas")
                        .contentType(APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Escenario: la salud es pública")
    void saludPublica() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
