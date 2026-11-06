package com.noticiasplaneta.news.domain;

import com.noticiasplaneta.shared.domain.Actor;

import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("F02 · Agregado Article")
class ArticleTest {

    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");

    @Test
    @DisplayName("Un artículo nace siempre en DRAFT")
    void naceEnDraft() {
        Article article = Article.draft("es", "economia", ArticleOrigin.INGEST, "item-1",
                Slug.fromTitle("Titular"), "Titular", "Cuerpo de prueba", NOW);

        assertThat(article.state()).isEqualTo(ArticleState.DRAFT);
        assertThat(article.revisionCount()).isEqualTo(1);
        assertThat(article.publishedAt()).isNull();
    }

    @Test
    @DisplayName("Sin título no hay artículo")
    void sinTitulo() {
        assertThatThrownBy(() -> Article.draft("es", "economia", ArticleOrigin.INGEST, null,
                Slug.fromTitle("x"), "  ", "Cuerpo de prueba", NOW))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("Al publicar se fija la fecha de publicación")
    void publicacionFijaFecha() {
        Article article = Article.draft("es", "economia", ArticleOrigin.MANUAL, null,
                Slug.fromTitle("Titular"), "Titular", "Cuerpo de prueba", NOW);
        Actor chief = Actor.human("u2", java.util.Set.of("CHIEF"), java.util.Set.of("economia"));

        article.transitionTo(ArticleState.IN_REVIEW, chief, NOW);
        article.transitionTo(ArticleState.APPROVED, chief, NOW);
        article.transitionTo(ArticleState.SCHEDULED, chief, NOW);
        article.transitionTo(ArticleState.PUBLISHED, chief, NOW);

        assertThat(article.state()).isEqualTo(ArticleState.PUBLISHED);
        assertThat(article.publishedAt()).isEqualTo(NOW);
    }

    // ---------- F13: edición ----------

    @Test
    @DisplayName("Editar cambia titular y cuerpo y sube la versión")
    void editar() {
        Article article = Article.draft("es", "economia", ArticleOrigin.MANUAL, null,
                Slug.fromTitle("Titular viejo"), "Titular viejo", "Cuerpo viejo", NOW);

        article.edit("Titular nuevo", "Cuerpo nuevo", 1);

        assertThat(article.title()).isEqualTo("Titular nuevo");
        assertThat(article.body()).isEqualTo("Cuerpo nuevo");
        assertThat(article.revisionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Editar el titular no mueve la URL")
    void editarNoCambiaSlug() {
        Article article = Article.draft("es", "economia", ArticleOrigin.MANUAL, null,
                Slug.fromTitle("Titular original"), "Titular original", "Cuerpo", NOW);
        String slugAntes = article.slug().value();

        article.edit("Otro titular completamente distinto", "Cuerpo", 1);

        assertThat(article.slug().value()).isEqualTo(slugAntes);
    }

    @Test
    @DisplayName("Una versión vieja no pisa el trabajo de otra persona")
    void conflictoDeVersion() {
        Article article = Article.draft("es", "economia", ArticleOrigin.MANUAL, null,
                Slug.fromTitle("Titular"), "Titular", "Cuerpo", NOW);
        article.edit("Primera edición", "Cuerpo 1", 1);

        assertThatThrownBy(() -> article.edit("Llego tarde", "Cuerpo 2", 1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("ha cambiado");
        assertThat(article.title()).isEqualTo("Primera edición");
    }

    @Test
    @DisplayName("Sin cuerpo no se guarda la edición")
    void editarSinCuerpo() {
        Article article = Article.draft("es", "economia", ArticleOrigin.MANUAL, null,
                Slug.fromTitle("Titular"), "Titular", "Cuerpo", NOW);

        assertThatThrownBy(() -> article.edit("Titular válido", "   ", 1))
                .isInstanceOf(DomainException.class);
        assertThat(article.body()).isEqualTo("Cuerpo");
    }
}
