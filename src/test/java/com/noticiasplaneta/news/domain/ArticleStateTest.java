package com.noticiasplaneta.news.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("F05 · Máquina de estados del artículo")
class ArticleStateTest {

    @Test
    @DisplayName("Las transiciones del flujo editorial son legales")
    void transicionesLegales() {
        assertThat(ArticleState.DRAFT.canTransitionTo(ArticleState.IN_REVIEW)).isTrue();
        assertThat(ArticleState.IN_REVIEW.canTransitionTo(ArticleState.APPROVED)).isTrue();
        assertThat(ArticleState.IN_REVIEW.canTransitionTo(ArticleState.DRAFT)).isTrue();
        assertThat(ArticleState.APPROVED.canTransitionTo(ArticleState.SCHEDULED)).isTrue();
        assertThat(ArticleState.SCHEDULED.canTransitionTo(ArticleState.PUBLISHED)).isTrue();
        assertThat(ArticleState.PUBLISHED.canTransitionTo(ArticleState.ARCHIVED)).isTrue();
    }

    @Test
    @DisplayName("No se puede saltar de draft a published")
    void noSePuedeSaltar() {
        assertThat(ArticleState.DRAFT.canTransitionTo(ArticleState.PUBLISHED)).isFalse();
    }

    @Test
    @DisplayName("ARCHIVED es terminal")
    void archivadoEsTerminal() {
        for (ArticleState state : ArticleState.values()) {
            assertThat(ArticleState.ARCHIVED.canTransitionTo(state)).isFalse();
        }
        assertThat(ArticleState.ARCHIVED.isTerminal()).isTrue();
    }
}
