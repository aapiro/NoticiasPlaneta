package com.noticiasplaneta.taxonomy;

import com.noticiasplaneta.news.application.port.in.CreateDraftCommand;
import com.noticiasplaneta.news.application.port.in.CreateDraftFromItemUseCase;
import com.noticiasplaneta.news.application.port.in.CreateDraftResult;
import com.noticiasplaneta.news.application.port.in.TransitionArticleUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionCommand;
import com.noticiasplaneta.shared.domain.Actor;
import com.noticiasplaneta.news.domain.ArticleOrigin;
import com.noticiasplaneta.news.domain.ArticleState;
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

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("F11 · Aceptación: tema, línea temporal y delta")
class TopicTimelineAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("media.root", () -> System.getProperty("java.io.tmpdir") + "/np-topic-test");
        registry.add("security.api-key", () -> "clave-test");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CreateDraftFromItemUseCase createDraft;

    @Autowired
    TransitionArticleUseCase transicion;

    private final Actor chief = Actor.human("u-chief", Set.of("CHIEF"), Set.of("economia"));
    private UUID topicId;
    private static final String TOPIC_SLUG = "sequia-en-el-duero";

    @BeforeEach
    void crearTema() {
        jdbc.update("delete from article_topic");
        jdbc.update("delete from topic");
        topicId = UUID.randomUUID();
        jdbc.update("insert into topic (id, slug, title, summary) values (?, ?, ?, ?)",
                topicId, TOPIC_SLUG, "Sequía en el Duero", "Seguimiento de la cuenca");
    }

    private String publicarEnTema(String titulo) {
        CreateDraftResult draft = createDraft.create(new CreateDraftCommand(
                UUID.randomUUID().toString(), "es", "economia", titulo, "Cuerpo de la noticia.",
                "https://ejemplo.es/x", ArticleOrigin.MANUAL));

        for (ArticleState state : new ArticleState[]{
                ArticleState.IN_REVIEW, ArticleState.APPROVED,
                ArticleState.SCHEDULED, ArticleState.PUBLISHED}) {
            transicion.transition(new TransitionCommand(draft.articleId(), state, chief, null));
        }

        jdbc.update("insert into article_topic (article_id, topic_id) values (?, ?)",
                UUID.fromString(draft.articleId().toString()), topicId);
        return draft.slug().value();
    }

    @Test
    @DisplayName("El tema muestra su línea temporal")
    void lineaTemporal() throws Exception {
        publicarEnTema("La cuenca del Duero entra en alerta");

        mvc.perform(get("/temas/" + TOPIC_SLUG))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("La cuenca del Duero entra en alerta")));
    }

    @Test
    @DisplayName("Sin fecha no hay bloque de delta ni se rompe")
    void sinFecha() throws Exception {
        publicarEnTema("Noticia de prueba del tema");

        String html = mvc.perform(get("/temas/" + TOPIC_SLUG))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("Desde tu última visita");
    }

    @Test
    @DisplayName("Con una fecha anterior, el bloque delta recoge lo publicado después")
    void conDelta() throws Exception {
        publicarEnTema("Publicada después de tu visita");

        String desde = Instant.now().minusSeconds(3600).toString();

        mvc.perform(get("/temas/" + TOPIC_SLUG).param("desde", desde))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Desde tu última visita")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Publicada después de tu visita")));
    }

    @Test
    @DisplayName("Con una fecha futura se trata como primera visita")
    void fechaFutura() throws Exception {
        publicarEnTema("Noticia anterior a la visita");

        String desde = Instant.now().plusSeconds(3600).toString();

        String html = mvc.perform(get("/temas/" + TOPIC_SLUG).param("desde", desde))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Desde tu última visita")
                .contains("Sin novedades desde tu última visita");
    }

    @Test
    @DisplayName("Un tema inexistente devuelve 404")
    void inexistente() throws Exception {
        mvc.perform(get("/tema/no-existe")).andExpect(status().isNotFound());
    }
}
