package com.noticiasplaneta.web;

import com.noticiasplaneta.news.application.port.in.CreateDraftCommand;
import com.noticiasplaneta.news.application.port.in.CreateDraftFromItemUseCase;
import com.noticiasplaneta.news.application.port.in.CreateDraftResult;
import com.noticiasplaneta.news.application.port.in.TransitionArticleUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionCommand;
import com.noticiasplaneta.news.application.port.in.UnpublishArticleUseCase;
import com.noticiasplaneta.news.application.port.in.UnpublishCommand;
import com.noticiasplaneta.news.domain.UnpublishKind;
import com.noticiasplaneta.news.domain.Actor;
import com.noticiasplaneta.news.domain.ArticleOrigin;
import com.noticiasplaneta.news.domain.ArticleState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Web · Aceptación: portada, artículo y buscador")
class WebPagesAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-web-test");
        registry.add("security.api-key", () -> "clave-test");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    CreateDraftFromItemUseCase createDraft;

    @Autowired
    TransitionArticleUseCase transicion;

    @Autowired
    UnpublishArticleUseCase unpublish;

    private final Actor chief = Actor.human("u-chief", Set.of("CHIEF"), Set.of("economia"));

    private String publicar(String titulo, String cuerpo) {
        CreateDraftResult draft = createDraft.create(new CreateDraftCommand(
                UUID.randomUUID().toString(), "es", "economia", titulo, cuerpo,
                "https://ejemplo.es/x", ArticleOrigin.MANUAL));
        for (ArticleState state : new ArticleState[]{
                ArticleState.IN_REVIEW, ArticleState.APPROVED,
                ArticleState.SCHEDULED, ArticleState.PUBLISHED}) {
            transicion.transition(new TransitionCommand(draft.articleId(), state, chief, null));
        }
        return draft.slug().value();
    }

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private String idDe(String slug) {
        return jdbc.queryForObject("select id::text from article where slug = ?", String.class, slug);
    }

    @Test
    @DisplayName("La portada muestra los titulares publicados")
    void portada() throws Exception {
        publicar("Sube el precio de la luz", "La factura sube un doce por ciento.");

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sube el precio de la luz")));
    }

    @Test
    @DisplayName("El artículo se sirve por sección y slug")
    void articulo() throws Exception {
        String slug = publicar("Noticia con cuerpo", "Primer párrafo del cuerpo.");

        mvc.perform(get("/economia/" + slug))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Primer párrafo")));
    }

    @Test
    @DisplayName("Un artículo retirado devuelve 410 Gone con la nota de retirada")
    void retirado() throws Exception {
        String slug = publicar("Noticia que se retira", "Cuerpo que dejará de verse.");
        String id = idDe(slug);

        unpublish.unpublish(new UnpublishCommand(
                com.noticiasplaneta.news.domain.ArticleId.of(id),
                UnpublishKind.RETIRADA,
                "La fuente principal rectificó y el dato no se sostiene", chief));

        mvc.perform(get("/economia/" + slug))
                .andExpect(status().isGone())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Artículo retirado")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("rectificó")))
                .andExpect(content().string(org.hamcrest.Matchers
                        .not(org.hamcrest.Matchers.containsString("Cuerpo que dejará de verse"))));
    }

    @Test
    @DisplayName("Un artículo que no existe devuelve 404")
    void inexistente() throws Exception {
        mvc.perform(get("/economia/no-existe")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El buscador encuentra por palabra del titular")
    void buscador() throws Exception {
        publicar("Acuerdo sobre el trasvase del Ebro", "Cuerpo de la noticia.");

        mvc.perform(get("/buscar").param("q", "trasvase"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("trasvase")));
    }

    @Test
    @DisplayName("El buscador no revienta con una consulta vacía")
    void buscadorVacio() throws Exception {
        mvc.perform(get("/buscar")).andExpect(status().isOk());
    }
}
