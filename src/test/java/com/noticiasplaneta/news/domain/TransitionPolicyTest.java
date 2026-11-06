package com.noticiasplaneta.news.domain;

import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("F05 · Autorización de transiciones")
class TransitionPolicyTest {

    private static final String ECONOMIA = "economia";

    @Test
    @DisplayName("Un AUTHOR no puede publicar")
    void authorNoPublica() {
        Actor author = Actor.human("u1", Set.of("AUTHOR"), Set.of(ECONOMIA));

        assertThatThrownBy(() -> TransitionPolicy.assertCanPerform(
                author, ArticleState.SCHEDULED, ArticleState.PUBLISHED, ECONOMIA))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("Un agente no puede publicar aunque tenga permisos de escritura")
    void agenteNoPublica() {
        Actor agent = Actor.agent("newsroom-redaccion", Set.of("CHIEF", "EDITOR", "AUTHOR"));

        assertThatThrownBy(() -> TransitionPolicy.assertCanPerform(
                agent, ArticleState.SCHEDULED, ArticleState.PUBLISHED, ECONOMIA))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("AGENT_CANNOT_PUBLISH");
    }

    @Test
    @DisplayName("Un CHIEF humano sí puede publicar")
    void chiefPublica() {
        Actor chief = Actor.human("u2", Set.of("CHIEF"), Set.of(ECONOMIA));

        assertThatCode(() -> TransitionPolicy.assertCanPerform(
                chief, ArticleState.SCHEDULED, ArticleState.PUBLISHED, ECONOMIA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un EDITOR no aprueba fuera de sus secciones")
    void editorFueraDeAlcance() {
        Actor editor = Actor.human("u3", Set.of("EDITOR"), Set.of(ECONOMIA));

        assertThatThrownBy(() -> TransitionPolicy.assertCanPerform(
                editor, ArticleState.IN_REVIEW, ArticleState.APPROVED, "deportes"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("OUT_OF_SCOPE");
    }

    @Test
    @DisplayName("Un EDITOR aprueba dentro de su sección")
    void editorEnSuSeccion() {
        Actor editor = Actor.human("u3", Set.of("EDITOR"), Set.of(ECONOMIA));

        assertThatCode(() -> TransitionPolicy.assertCanPerform(
                editor, ArticleState.IN_REVIEW, ArticleState.APPROVED, ECONOMIA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Una transición ilegal se rechaza con ILLEGAL_TRANSITION")
    void transicionIlegal() {
        Actor chief = Actor.human("u2", Set.of("CHIEF"), Set.of(ECONOMIA));

        assertThatThrownBy(() -> TransitionPolicy.assertCanPerform(
                chief, ArticleState.DRAFT, ArticleState.PUBLISHED, ECONOMIA))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("ILLEGAL_TRANSITION");
    }
}
