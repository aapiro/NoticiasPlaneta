package com.noticiasplaneta.news.domain;

import com.noticiasplaneta.shared.domain.Actor;
import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("F05 · Publicación inmediata y camino por la máquina de estados")
class PublicacionPorLoteTest {

    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");
    private static final String ECONOMIA = "economia";

    private final Actor chief = Actor.human("u-chief", Set.of("CHIEF"), Set.of(ECONOMIA));
    private final Actor editor = Actor.human("u-editor", Set.of("EDITOR"), Set.of(ECONOMIA));
    private final Actor agent = Actor.agent("newsroom", Set.of("CHIEF", "EDITOR", "ADMIN"));

    private Article borrador() {
        return Article.draft("es", ECONOMIA, ArticleOrigin.MANUAL, null,
                Slug.fromTitle("Reforma fiscal"), "Reforma fiscal", "Cuerpo de prueba", NOW);
    }

    @Test
    @DisplayName("El camino de un borrador a publicado pasa por revisión, aprobación y programación")
    void caminoDelBorrador() {
        assertThat(ArticleState.DRAFT.pathTo(ArticleState.PUBLISHED))
                .containsExactly(ArticleState.IN_REVIEW, ArticleState.APPROVED,
                        ArticleState.SCHEDULED, ArticleState.PUBLISHED);
    }

    @Test
    @DisplayName("Desde un estado terminal no hay camino a publicado")
    void archivadoNoLlega() {
        assertThat(ArticleState.ARCHIVED.pathTo(ArticleState.PUBLISHED)).isEmpty();
        assertThat(ArticleState.PUBLISHED.pathTo(ArticleState.PUBLISHED)).isEmpty();
    }

    @Test
    @DisplayName("Publicar un borrador deja PUBLISHED, con fecha, y sin programación residual")
    void publicarBorrador() {
        Article article = borrador();

        ArticleState anterior = article.publishNow(chief, NOW);

        assertThat(anterior).isEqualTo(ArticleState.DRAFT);
        assertThat(article.state()).isEqualTo(ArticleState.PUBLISHED);
        assertThat(article.publishedAt()).isEqualTo(NOW);
        assertThat(article.scheduledAt())
                .as("no queda una programación residual en scheduled_at")
                .isNull();
    }

    @Test
    @DisplayName("Publicar lo que ya está aprobado tampoco inventa aristas")
    void publicarAprobado() {
        Article article = borrador();
        article.transitionTo(ArticleState.IN_REVIEW, chief, NOW);
        article.transitionTo(ArticleState.APPROVED, chief, NOW);

        article.publishNow(chief, NOW);

        assertThat(article.state()).isEqualTo(ArticleState.PUBLISHED);
    }

    @Test
    @DisplayName("Un artículo ya publicado no se vuelve a publicar")
    void yaPublicado() {
        Article article = borrador();
        article.publishNow(chief, NOW);

        assertThatThrownBy(() -> article.publishNow(chief, NOW))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("ALREADY_PUBLISHED");
    }

    @Test
    @DisplayName("Un archivado no se publica")
    void archivado() {
        Article article = borrador();
        article.publishNow(chief, NOW);
        article.transitionTo(ArticleState.ARCHIVED, chief, NOW);

        assertThatThrownBy(() -> article.publishNow(chief, NOW))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("ARTICLE_ARCHIVED");
    }

    @Test
    @DisplayName("Un EDITOR sin rol CHIEF no publica, aunque el artículo sea suyo")
    void editorNoPublica() {
        Article article = borrador();

        assertThatThrownBy(() -> article.publishNow(editor, NOW))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("FORBIDDEN");
        assertThat(article.state()).as("no se ha tocado el estado").isEqualTo(ArticleState.DRAFT);
    }

    @Test
    @DisplayName("Un agente no publica ni en lote (RN5)")
    void agenteNoPublica() {
        Article article = borrador();

        assertThatThrownBy(() -> article.publishNow(agent, NOW))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("AGENT_CANNOT_PUBLISH");
    }

    @Test
    @DisplayName("La política deja pasar al CHIEF sin mirar el estado")
    void politicaDelLote() {
        TransitionPolicy.assertCanPublish(chief);
        assertThatThrownBy(() -> TransitionPolicy.assertCanPublish(editor))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> TransitionPolicy.assertCanPublish(agent))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("AGENT_CANNOT_PUBLISH");
    }

    @Test
    @DisplayName("El camino no depende del estado de partida que ya está en la máquina")
    void caminosIntermedios() {
        assertThat(ArticleState.IN_REVIEW.pathTo(ArticleState.PUBLISHED))
                .containsExactly(ArticleState.APPROVED, ArticleState.SCHEDULED, ArticleState.PUBLISHED);
        assertThat(ArticleState.SCHEDULED.pathTo(ArticleState.PUBLISHED))
                .containsExactly(ArticleState.PUBLISHED);
        assertThat(List.of(ArticleState.APPROVED))
                .allMatch(estado -> !estado.pathTo(ArticleState.PUBLISHED).isEmpty());
    }
}
