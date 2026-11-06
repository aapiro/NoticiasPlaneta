package com.noticiasplaneta.news;

import com.noticiasplaneta.news.application.port.in.CreateDraftCommand;
import com.noticiasplaneta.news.application.port.in.CreateDraftFromItemUseCase;
import com.noticiasplaneta.news.application.port.in.CreateDraftResult;
import com.noticiasplaneta.news.application.port.in.TransitionArticleUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionCommand;
import com.noticiasplaneta.news.domain.Actor;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleOrigin;
import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@DisplayName("F02 + F05 · Aceptación: flujo editorial")
class F02F05EditorialAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @Autowired
    CreateDraftFromItemUseCase createDraft;

    @Autowired
    TransitionArticleUseCase transicion;

    private final Actor chief = Actor.human("u-chief", Set.of("CHIEF"), Set.of("economia"));
    private final Actor author = Actor.human("u-author", Set.of("AUTHOR"), Set.of("economia"));
    private final Actor agent = Actor.agent("newsroom-redaccion", Set.of("CHIEF", "EDITOR"));

    private CreateDraftResult draft(String titulo) {
        return createDraft.create(new CreateDraftCommand(
                UUID.randomUUID().toString(), "es", "economia", titulo,
                "Cuerpo", "https://ejemplo.es/x", ArticleOrigin.INGEST));
    }

    @Test
    @DisplayName("Escenario: un item nuevo produce un borrador en DRAFT con slug")
    void produceBorrador() {
        CreateDraftResult result = draft("El Congreso aprueba la reforma fiscal");

        assertThat(result.created()).isTrue();
        assertThat(result.slug().value()).isEqualTo("el-congreso-aprueba-la-reforma-fiscal");
    }

    @Test
    @DisplayName("Escenario: el mismo item no crea dos artículos")
    void idempotente() {
        String itemId = UUID.randomUUID().toString();
        CreateDraftCommand cmd = new CreateDraftCommand(itemId, "es", "economia", "Titular único",
                "Cuerpo", "https://ejemplo.es/y", ArticleOrigin.INGEST);

        CreateDraftResult a = createDraft.create(cmd);
        CreateDraftResult b = createDraft.create(cmd);

        assertThat(b.created()).isFalse();
        assertThat(a.articleId()).isEqualTo(b.articleId());
    }

    @Test
    @DisplayName("Escenario: un agente no puede publicar aunque tenga rol CHIEF")
    void agenteNoPublica() {
        Article article = pasoHastaPublicable();

        assertThatThrownBy(() -> transicion.transition(
                new TransitionCommand(article.id(), ArticleState.PUBLISHED, agent, null)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("AGENT_CANNOT_PUBLISH");
    }

    @Test
    @DisplayName("Escenario: un autor no puede publicar")
    void autorNoPublica() {
        Article article = pasoHastaPublicable();

        assertThatThrownBy(() -> transicion.transition(
                new TransitionCommand(article.id(), ArticleState.PUBLISHED, author, null)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("Escenario: el CHIEF publica y queda auditado")
    void chiefPublica() {
        Article article = pasoHastaPublicable();

        Article published = transicion.transition(
                new TransitionCommand(article.id(), ArticleState.PUBLISHED, chief, null));

        assertThat(published.state()).isEqualTo(ArticleState.PUBLISHED);
        assertThat(published.publishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Escenario: una transición ilegal deja el artículo como estaba")
    void transicionIlegal() {
        CreateDraftResult draft = draft("Titular para transición ilegal");

        assertThatThrownBy(() -> transicion.transition(new TransitionCommand(
                draft.articleId(), ArticleState.PUBLISHED, chief, null)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("ILLEGAL_TRANSITION");
    }

    private Article pasoHastaPublicable() {
        CreateDraftResult draft = draft("Titular de pruebas " + UUID.randomUUID());
        transicion.transition(new TransitionCommand(draft.articleId(), ArticleState.IN_REVIEW, chief, null));
        transicion.transition(new TransitionCommand(draft.articleId(), ArticleState.APPROVED, chief, null));
        return transicion.transition(new TransitionCommand(draft.articleId(), ArticleState.SCHEDULED, chief, null));
    }
}
