package com.noticiasplaneta.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Backlog #3 · Paginación por secciones")
class PaginacionAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-pag-test");
        registry.add("security.api-key", () -> "clave-test");
        registry.add("admin.bootstrap.email", () -> "");
        registry.add("noticias.base-url", () -> "https://noticiasplaneta.com");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private static final int PUBLICADAS = 30;
    private static final int TAMANO = 24;

    @BeforeEach
    void sembrar() {
        // Idempotente: si el contexto ya sembro, no se duplica.
        if (jdbc.queryForObject("select count(*) from article where state = 'PUBLISHED'", Integer.class) > 0) {
            return;
        }
        for (int i = 0; i < PUBLICADAS; i++) {
            String id = UUID.randomUUID().toString();
            String slug = "nota-pagina-" + i;
            jdbc.update("""
                    insert into article (id, language, section_id, origin, slug, title, body, state,
                                         published_at, created_at)
                    values (?::uuid, 'es', 'general', 'INGEST', ?, ?, 'cuerpo', 'PUBLISHED',
                            now() - (? || ' minutes')::interval, now())""",
                    id, slug, "Noticia número " + i, i);
        }
    }

    @Test
    @DisplayName("La portada corta en 24 y la segunda página trae el resto")
    void portadaEnDosPaginas() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Noticia número 0")));

        mvc.perform(get("/").param("pagina", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Página 2 de 2")));

        // 30 publicadas y 24 por página: la segunda trae 6 piezas. La primera de cada
        // página se pinta como apertura, así que se cuentan aperturas + tarjetas.
        String segunda = mvc.perform(get("/").param("pagina", "2"))
                .andReturn().getResponse().getContentAsString();
        int piezas = segunda.split("class=\"hero\"", -1).length - 1
                + segunda.split("class=\"tarjeta\"", -1).length - 1;
        org.assertj.core.api.Assertions.assertThat(piezas).isEqualTo(6);

        String primera = mvc.perform(get("/"))
                .andReturn().getResponse().getContentAsString();
        int enPrimera = primera.split("class=\"hero\"", -1).length - 1
                + primera.split("class=\"tarjeta\"", -1).length - 1;
        org.assertj.core.api.Assertions.assertThat(enPrimera).isEqualTo(TAMANO);
    }

    @Test
    @DisplayName("Una página más allá del final devuelve vacío, no la última")
    void paginaFueraDeRango() throws Exception {
        mvc.perform(get("/").param("pagina", "99"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Todavía no hay artículos publicados")));
    }

    @Test
    @DisplayName("La sección declara su enfoque y pagina sobre su propio listado")
    void seccionConEnfoque() throws Exception {
        mvc.perform(get("/seccion/tecnologia"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Enfoque neutral")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "no autoriza a inventar")));
    }

    @Test
    @DisplayName("Una sección inexistente responde 404")
    void seccionInexistente() throws Exception {
        mvc.perform(get("/seccion/no-existe")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El canonical y el rel=next no mienten sobre la página")
    void seoDePaginacion() throws Exception {
        String html = mvc.perform(get("/"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html)
                .contains("rel=\"next\"")
                .contains("?pagina=2");
    }
}
