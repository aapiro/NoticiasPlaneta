package com.noticiasplaneta.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Backlog #1 · SEO: sitemap, robots, Open Graph y JSON-LD")
class SeoAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-seo-test");
        registry.add("security.api-key", () -> "clave-test");
        registry.add("admin.bootstrap.email", () -> "");
        registry.add("noticias.base-url", () -> "https://noticiasplaneta.test");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private static final String SLUG = "nota-de-seo";

    @BeforeEach
    void sembrarPublicada() {
        Integer ya = jdbc.queryForObject(
                "select count(*) from article where slug = ?", Integer.class, SLUG);
        if (ya != null && ya > 0) return;

        UUID sourceId = UUID.randomUUID();
        jdbc.update("""
                insert into source (id, name, feed_url, state, failure_count, confianza)
                values (?, 'Medio SEO', 'https://seo.example/rss', 'ACTIVE', 0, true)""", sourceId);

        UUID itemId = UUID.randomUUID();
        jdbc.update("""
                insert into ingest_item
                  (id, source_id, external_id, content_hash, title, body, canonical_url,
                   published_at, author, image_url, created_at)
                values (?, ?, 'ext-seo', 'hash-seo', ?, ?, 'https://seo.example/nota',
                        now(), 'Redacción', 'https://seo.example/foto.jpg', now())""",
                itemId, sourceId,
                "Una noticia con foto para probar el SEO",
                "<p><img src=\"https://seo.example/foto.jpg\">Un cuerpo con <b>HTML</b> que no "
                        + "debe llegar a la descripción.</p>");

        jdbc.update("""
                insert into article (id, language, section_id, origin, ingest_item_id, slug,
                                     title, body, state, published_at, created_at)
                values (?, 'es', 'general', 'INGEST', ?, ?, ?, 'cuerpo', 'PUBLISHED', now(), now())""",
                UUID.randomUUID(), itemId.toString(), SLUG,
                "Una noticia con foto para probar el SEO");
    }

    @Test
    @DisplayName("robots.txt apunta al sitemap y bloquea el panel")
    void robots() throws Exception {
        String cuerpo = mvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).contains("Disallow: /admin")
                .contains("Sitemap: https://noticiasplaneta.test/sitemap.xml");
    }

    @Test
    @DisplayName("sitemap.xml lista la portada, las secciones y la publicada")
    void sitemap() throws Exception {
        String xml = mvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(xml).startsWith("<?xml")
                .contains("<urlset")
                .contains("https://noticiasplaneta.test/")
                .contains("https://noticiasplaneta.test/general/" + SLUG);
    }

    @Test
    @DisplayName("El artículo emite Open Graph, canonical y JSON-LD de NewsArticle")
    void openGraphYJsonLd() throws Exception {
        String html = mvc.perform(get("/general/" + SLUG))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("rel=\"canonical\"")
                .contains("https://noticiasplaneta.test/general/" + SLUG)
                .contains("property=\"og:title\"")
                .contains("property=\"og:image\"")
                .contains("https://seo.example/foto.jpg")
                .contains("\"@type\":\"NewsArticle\"")
                .contains("\"datePublished\"");
    }

    @Test
    @DisplayName("La descripción no lleva HTML crudo del feed")
    void descripcionSinHtml() throws Exception {
        String html = mvc.perform(get("/general/" + SLUG))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int desde = html.indexOf("<meta name=\"description\"");
        String etiqueta = html.substring(desde, html.indexOf('>', desde));
        assertThat(etiqueta).doesNotContain("&lt;p&gt;").doesNotContain("<img");
    }
}
