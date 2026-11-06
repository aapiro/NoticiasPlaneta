package com.noticiasplaneta.news;

import com.noticiasplaneta.news.application.port.in.CreateDraftCommand;
import com.noticiasplaneta.news.application.port.in.CreateDraftFromItemUseCase;
import com.noticiasplaneta.news.application.port.in.CreateDraftResult;
import com.noticiasplaneta.news.application.port.in.TransitionArticleUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionCommand;
import com.noticiasplaneta.news.application.port.in.UnpublishArticleUseCase;
import com.noticiasplaneta.news.application.port.in.UnpublishCommand;
import com.noticiasplaneta.news.domain.Actor;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleOrigin;
import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.news.domain.UnpublishKind;
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
@DisplayName("F07 · Aceptación: despublicación")
class F07DespublicacionAcceptanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.5-pg16");

    @Autowired
    CreateDraftFromItemUseCase createDraft;

    @Autowired
    TransitionArticleUseCase transicion;

    @Autowired
    UnpublishArticleUseCase unpublish;

    private final Actor chief = Actor.human("u-chief", Set.of("CHIEF"), Set.of("economia"));

    private Article articuloPublicado() {
        CreateDraftResult draft = createDraft.create(new CreateDraftCommand(
                UUID.randomUUID().toString(), "es", "economia",
                "Titular " + UUID.randomUUID(), "Cuerpo", "https://ejemplo.es/x", ArticleOrigin.MANUAL));
        transicion.transition(new TransitionCommand(draft.articleId(), ArticleState.IN_REVIEW, chief, null));
        transicion.transition(new TransitionCommand(draft.articleId(), ArticleState.APPROVED, chief, null));
        transicion.transition(new TransitionCommand(draft.articleId(), ArticleState.SCHEDULED, chief, null));
        return transicion.transition(new TransitionCommand(draft.articleId(), ArticleState.PUBLISHED, chief, null));
    }

    @Test
    @DisplayName("Escenario: despublicar archiva y conserva el motivo")
    void despublica() {
        Article publicado = articuloPublicado();

        Article archivado = unpublish.unpublish(new UnpublishCommand(
                publicado.id(), UnpublishKind.RETIRADA,
                "La fuente principal rectificó y el dato no se sostiene", chief));

        assertThat(archivado.state()).isEqualTo(ArticleState.ARCHIVED);
        assertThat(archivado.unpublishReason()).contains("rectificó");
        assertThat(archivado.unpublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Escenario: sin motivo suficiente se rechaza")
    void sinMotivo() {
        Article publicado = articuloPublicado();

        assertThatThrownBy(() -> unpublish.unpublish(new UnpublishCommand(
                publicado.id(), UnpublishKind.RETIRADA, "no", chief)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("REASON_REQUIRED");
    }

    @Test
    @DisplayName("Escenario: una corrección mantiene publicado con nota")
    void corrige() {
        Article publicado = articuloPublicado();

        Article corregido = unpublish.unpublish(new UnpublishCommand(
                publicado.id(), UnpublishKind.CORRECCION,
                "Se corrige el número de afiliados al dato del ministerio", chief));

        assertThat(corregido.state()).isEqualTo(ArticleState.PUBLISHED);
        assertThat(corregido.correctionNote()).contains("afiliados");
    }
}
