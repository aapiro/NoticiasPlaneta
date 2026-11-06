package com.noticiasplaneta.media;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("F08 · Aceptación: medios")
class F08MediosAcceptanceTest {

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    static final Path MEDIA_ROOT;

    static {
        try {
            MEDIA_ROOT = Files.createTempDirectory("np-media-test");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> MEDIA_ROOT.toString());
        registry.add("media.signing-secret", () -> "test-secret");
        registry.add("security.api-key", () -> "test-api-key");
        registry.add("security.agent-id", () -> "newsroom-test");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private JsonNode presign(String contentType, long size) throws Exception {
        String body = """
                {"filename":"foto.png","contentType":"%s","sizeBytes":%d,"uploaderId":"u1"}
                """.formatted(contentType, size);
        String response = mvc.perform(post("/api/v1/media/presign")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    @Test
    @DisplayName("Escenario: subida completa firma → PUT → commit registra el activo")
    void subidaCompleta() throws Exception {
        byte[] png = pngBytes(120, 80);
        JsonNode presigned = presign("image/png", png.length);

        String uploadUrl = presigned.get("uploadUrl").asText();
        String key = presigned.get("storageKey").asText();

        mvc.perform(put(uploadUrl).contentType("image/png").content(png))
                .andExpect(status().isOk());

        String commitBody = """
                {"storageKey":"%s","uploaderId":"u1","altText":"Un autobús siniestrado en la cuneta","credit":"Redacción"}
                """.formatted(key);

        mvc.perform(post("/api/v1/media/commit").header("X-Api-Key", "test-api-key").contentType(APPLICATION_JSON).content(commitBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mime").value("image/png"))
                .andExpect(jsonPath("$.width").value(120))
                .andExpect(jsonPath("$.height").value(80))
                .andExpect(jsonPath("$.deduplicated").value(false));
    }

    @Test
    @DisplayName("Escenario: el mismo binario dos veces se deduplica")
    void deduplica() throws Exception {
        byte[] png = pngBytes(64, 64);

        JsonNode first = presign("image/png", png.length);
        mvc.perform(put(first.get("uploadUrl").asText()).contentType("image/png").content(png));
        String firstBody = "{\"storageKey\":\"%s\",\"uploaderId\":\"u1\",\"altText\":\"El mismo autobús, misma foto\"}"
                .formatted(first.get("storageKey").asText());
        mvc.perform(post("/api/v1/media/commit").header("X-Api-Key", "test-api-key").contentType(APPLICATION_JSON).content(firstBody))
                .andExpect(status().isCreated());

        JsonNode second = presign("image/png", png.length);
        mvc.perform(put(second.get("uploadUrl").asText()).contentType("image/png").content(png));
        String secondBody = "{\"storageKey\":\"%s\",\"uploaderId\":\"u1\",\"altText\":\"El mismo autobús, misma foto\"}"
                .formatted(second.get("storageKey").asText());
        mvc.perform(post("/api/v1/media/commit").header("X-Api-Key", "test-api-key").contentType(APPLICATION_JSON).content(secondBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deduplicated").value(true));
    }

    @Test
    @DisplayName("Escenario: una URL de subida sin firma válida se rechaza")
    void firmaInvalida() throws Exception {
        int code = mvc.perform(put("/api/v1/media/blob/tmp/x?exp=9999999999&token=inventado")
                        .contentType("image/png").content(pngBytes(10, 10)))
                .andReturn().getResponse().getStatus();

        assertThat(code).isEqualTo(400);
    }

    @Test
    @DisplayName("Escenario: el presign rechaza SVG")
    void rechazaSvg() throws Exception {
        mvc.perform(post("/api/v1/media/presign")
                        .header("X-Api-Key", "test-api-key")
                        .contentType(APPLICATION_JSON)
                        .content("{\"filename\":\"a.svg\",\"contentType\":\"image/svg+xml\",\"sizeBytes\":10,\"uploaderId\":\"u1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MIME_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Escenario: sin texto alternativo el commit se rechaza y el objeto no queda registrado")
    void sinTextoAlternativo() throws Exception {
        byte[] png = pngBytes(140, 90);
        JsonNode presigned = presign("image/png", png.length);
        String uploadUrl = presigned.get("uploadUrl").asText();
        String key = presigned.get("storageKey").asText();

        mvc.perform(put(uploadUrl).contentType("image/png").content(png))
                .andExpect(status().isOk());

        String sinAlt = """
                {"storageKey":"%s","uploaderId":"u1"}
                """.formatted(key);

        mvc.perform(post("/api/v1/media/commit").header("X-Api-Key", "test-api-key")
                        .contentType(APPLICATION_JSON).content(sinAlt))
                .andExpect(status().isBadRequest());

        Integer registrados = jdbc.queryForObject(
                "select count(*) from media_asset where storage_key = ?", Integer.class, key);
        org.assertj.core.api.Assertions.assertThat(registrados).isZero();
    }
}
