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
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Backlog #5 · RSS de salida")
class FeedAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-feed-test");
        registry.add("security.api-key", () -> "clave-test");
        registry.add("admin.bootstrap.email", () -> "");
        registry.add("noticias.base-url", () -> "https://noticiasplaneta.com");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void sembrar() {
        if (jdbc.queryForObject("select count(*) from article where state = 'PUBLISHED'", Integer.class) > 0) {
            return;
        }
        // Ampersand y comillas a propósito: el XML tiene que sobrevivir al escapado.
        jdbc.update("""
                insert into article (id, language, section_id, origin, slug, title, body, state,
                                     published_at, created_at)
                values (?::uuid, 'es', 'tecnologia', 'INGEST', 'nota-feed-1',
                        'Chrome & Firefox "compiten"', '<p>Un cuerpo con <b>negrita</b> &amp; entidades.</p>',
                        'PUBLISHED', now(), now())""", UUID.randomUUID().toString());
        jdbc.update("""
                insert into article (id, language, section_id, origin, slug, title, body, state,
                                     published_at, created_at)
                values (?::uuid, 'es', 'general', 'INGEST', 'nota-feed-2',
                        'Otra noticia', '<p>Segundo cuerpo.</p>', 'PUBLISHED',
                        now() - interval '1 hour', now())""", UUID.randomUUID().toString());
    }

    private Document parsear(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("El feed del sitio es XML válido, con canal, items y guid permanente")
    void feedDelSitio() throws Exception {
        var result = mvc.perform(get("/feed.xml"))
                .andExpect(status().isOk())
                .andReturn();
        String xml = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(result.getResponse().getContentType())
                .as("sin el content-type correcto los lectores no lo reconocen")
                .contains("application/rss+xml");

        Document doc = parsear(xml);
        assertThat(doc.getDocumentElement().getNodeName()).isEqualTo("rss");
        assertThat(doc.getDocumentElement().getAttribute("version")).isEqualTo("2.0");

        var items = doc.getElementsByTagName("item");
        assertThat(items.getLength()).isEqualTo(2);

        // El ampersand del titular no puede romper el XML ni llegar sin escapar.
        assertThat(xml).contains("Chrome &amp; Firefox");
        assertThat(xml).doesNotContain("Chrome & Firefox");

        var guids = doc.getElementsByTagName("guid");
        for (int i = 0; i < guids.getLength(); i++) {
            var guid = (org.w3c.dom.Element) guids.item(i);
            assertThat(guid.getAttribute("isPermaLink")).isEqualTo("true");
            assertThat(guid.getTextContent()).startsWith("https://noticiasplaneta.com/");
        }
    }

    @Test
    @DisplayName("La descripción sale como texto, sin etiquetas del cuerpo")
    void descripcionSinHtml() throws Exception {
        String xml = mvc.perform(get("/feed.xml"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document doc = parsear(xml);
        var descripciones = doc.getElementsByTagName("description");
        StringBuilder todas = new StringBuilder();
        for (int i = 0; i < descripciones.getLength(); i++) {
            todas.append(descripciones.item(i).getTextContent()).append('\n');
        }
        assertThat(todas.toString())
                .contains("Un cuerpo con negrita")
                .doesNotContain("<p>")
                .doesNotContain("<b>");
    }

    @Test
    @DisplayName("Cada sección tiene su propio feed y filtra por ella")
    void feedDeSeccion() throws Exception {
        String xml = mvc.perform(get("/seccion/tecnologia/feed.xml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document doc = parsear(xml);
        var items = doc.getElementsByTagName("item");
        assertThat(items.getLength()).isEqualTo(1);
        assertThat(xml).contains("Chrome &amp; Firefox");
        assertThat(xml).doesNotContain("Otra noticia");
    }

    @Test
    @DisplayName("Una sección inexistente no tiene feed")
    void seccionInexistente() throws Exception {
        mvc.perform(get("/seccion/no-existe/feed.xml")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El portal anuncia el feed para que lo descubran solos")
    void autodescubrimiento() throws Exception {
        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("application/rss+xml").contains("/feed.xml");
    }
}
