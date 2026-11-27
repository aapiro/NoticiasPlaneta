package com.noticiasplaneta.media;

import com.noticiasplaneta.media.application.port.in.CacheFeedImageUseCase;
import com.noticiasplaneta.media.application.port.out.ObjectStoragePort;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@DisplayName("Backlog #2 · La foto del feed se cachea y se sirve desde casa")
class CacheFeedImageAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-cache-test");
        registry.add("security.api-key", () -> "clave-test");
        registry.add("admin.bootstrap.email", () -> "");
    }

    /** Servidor local que hace de "medio de origen". */
    private static HttpServer origen;
    private static byte[] jpeg;

    @BeforeAll
    static void arrancarOrigen() throws Exception {
        BufferedImage img = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        jpeg = out.toByteArray();

        origen = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origen.createContext("/foto.jpg", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, jpeg.length);
            exchange.getResponseBody().write(jpeg);
            exchange.close();
        });
        origen.createContext("/nada.png", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        origen.createContext("/webp.png", exchange -> {
            // WebP disfrazado de png: el Content-Type manda, no la extension.
            exchange.getResponseHeaders().add("Content-Type", "image/webp");
            exchange.sendResponseHeaders(200, jpeg.length);
            exchange.getResponseBody().write(jpeg);
            exchange.close();
        });
        origen.start();
    }

    @AfterAll
    static void pararOrigen() {
        if (origen != null) origen.stop(0);
    }

    @Autowired
    CacheFeedImageUseCase cache;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectStoragePort storage;

    private String articulo;

    @BeforeEach
    void crearArticulo() {
        articulo = UUID.randomUUID().toString();
        jdbc.update("""
                insert into article (id, language, section_id, origin, slug, title, body, state, created_at)
                values (?, 'es', 'general', 'INGEST', ?, 'Noticia con foto', 'cuerpo', 'DRAFT', now())""",
                UUID.fromString(articulo), "nota-" + articulo.substring(0, 8));
    }

    private String url(String ruta) {
        return "http://127.0.0.1:" + origen.getAddress().getPort() + ruta;
    }

    @Test
    @DisplayName("Descarga, guarda por huella y la adjunta como imagen principal")
    void cacheaYAdjunta() {
        Optional<String> mediaId = cache.cacheFromUrl(url("/foto.jpg"), articulo, "Foto de la noticia de prueba");

        assertThat(mediaId).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from media_asset", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select role from article_media where article_id = ?::uuid", String.class, articulo))
                .isEqualTo("LEAD");

        String clave = jdbc.queryForObject("select storage_key from media_asset", String.class);
        assertThat(storage.get(clave)).isNotEmpty();
    }

    @Test
    @DisplayName("La misma foto en dos noticias ocupa un solo activo")
    void deduplica() {
        cache.cacheFromUrl(url("/foto.jpg"), articulo, "Primera noticia con la foto");

        String otro = UUID.randomUUID().toString();
        jdbc.update("""
                insert into article (id, language, section_id, origin, slug, title, body, state, created_at)
                values (?, 'es', 'general', 'INGEST', ?, 'Otra noticia', 'cuerpo', 'DRAFT', now())""",
                UUID.fromString(otro), "otra-" + otro.substring(0, 8));

        cache.cacheFromUrl(url("/foto.jpg"), otro, "Segunda noticia con la misma foto");

        assertThat(jdbc.queryForObject("select count(*) from media_asset", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from article_media where article_id in (?::uuid, ?::uuid)",
                Integer.class, articulo, otro)).isEqualTo(2);
    }

    @Test
    @DisplayName("Una URL que no existe no rompe: devuelve vacío")
    void urlRota() {
        assertThat(cache.cacheFromUrl(url("/nada.png"), articulo, "No hay foto")).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from media_asset", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Un WebP disfrazado no se guarda como si fuera PNG")
    void webpNoSeCuela() {
        assertThat(cache.cacheFromUrl(url("/webp.png"), articulo, "Formato no soportado")).isEmpty();
    }
}
