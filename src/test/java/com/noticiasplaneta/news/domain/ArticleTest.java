package com.noticiasplaneta.news.domain;

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
}
