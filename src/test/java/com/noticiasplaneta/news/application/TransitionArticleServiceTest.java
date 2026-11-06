package com.noticiasplaneta.news.application;

import com.noticiasplaneta.ingest.support.InMemoryEventPublisher;
import com.noticiasplaneta.news.application.port.in.TransitionCommand;
import com.noticiasplaneta.shared.events.ArticleStateChanged;
import com.noticiasplaneta.news.application.service.TransitionArticleService;
import com.noticiasplaneta.shared.domain.Actor;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleOrigin;
import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.news.domain.Slug;
import com.noticiasplaneta.news.support.InMemoryArticles;
import com.noticiasplaneta.news.support.InMemoryAudit;
import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("F05 · Caso de uso: transición de estado")
class TransitionArticleServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");
    private static final String ECONOMIA = "economia";

    private InMemoryArticles articles;
    private InMemoryAudit audit;
    private InMemoryEventPublisher events;
    private TransitionArticleService service;
    private Article article;

    private final Actor chief = Actor.human("u-chief", Set.of("CHIEF"), Set.of(ECONOMIA));
    private final Actor author = Actor.human("u-author", Set.of("AUTHOR"), Set.of(ECONOMIA));
    private final Actor agent = Actor.agent("newsroom-redaccion", Set.of("CHIEF", "EDITOR"));

    @BeforeEach
    void setUp() {
        articles = new InMemoryArticles();
        audit = new InMemoryAudit();
        events = new InMemoryEventPublisher();
        service = new TransitionArticleService(articles, audit, events, Clock.fixed(NOW, ZoneOffset.UTC));
        article = articles.save(Article.draft("es", ECONOMIA, ArticleOrigin.MANUAL, null,
                Slug.fromTitle("Reforma fiscal"), "Reforma fiscal", "Cuerpo de prueba", NOW));
    }

    @Test
    @DisplayName("Una transición válida cambia el estado, audita y emite evento")
    void transicionValida() {
        Article result = service.transition(
                new TransitionCommand(article.id(), ArticleState.IN_REVIEW, author, null));

        assertThat(result.state()).isEqualTo(ArticleState.IN_REVIEW);
        assertThat(audit.entries()).hasSize(1);
        assertThat(audit.entries().getFirst().before()).isEqualTo(ArticleState.DRAFT.name());
        assertThat(audit.entries().getFirst().after()).isEqualTo(ArticleState.IN_REVIEW.name());
        assertThat(audit.entries().getFirst().actorType()).isEqualTo("HUMAN");
        assertThat(events.published()).hasSize(1).first().isInstanceOf(ArticleStateChanged.class);
    }

    @Test
    @DisplayName("Una transición ilegal no cambia el estado y no audita")
    void transicionIlegal() {
        assertThatThrownBy(() -> service.transition(
                new TransitionCommand(article.id(), ArticleState.PUBLISHED, chief, null)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("ILLEGAL_TRANSITION");

        assertThat(articles.findById(article.id()).orElseThrow().state()).isEqualTo(ArticleState.DRAFT);
        assertThat(audit.entries()).isEmpty();
    }

    @Test
    @DisplayName("Un agente no publica ni con rol CHIEF")
    void agenteNoPublica() {
        article.transitionTo(ArticleState.IN_REVIEW, chief, NOW);
        article.transitionTo(ArticleState.APPROVED, chief, NOW);
        article.transitionTo(ArticleState.SCHEDULED, chief, NOW);
        articles.save(article);

        assertThatThrownBy(() -> service.transition(
                new TransitionCommand(article.id(), ArticleState.PUBLISHED, agent, null)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("AGENT_CANNOT_PUBLISH");
    }

    @Test
    @DisplayName("Un autor no publica")
    void autorNoPublica() {
        article.transitionTo(ArticleState.IN_REVIEW, chief, NOW);
        article.transitionTo(ArticleState.APPROVED, chief, NOW);
        article.transitionTo(ArticleState.SCHEDULED, chief, NOW);
        articles.save(article);

        assertThatThrownBy(() -> service.transition(
                new TransitionCommand(article.id(), ArticleState.PUBLISHED, author, null)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("Un artículo inexistente devuelve ARTICLE_NOT_FOUND")
    void inexistente() {
        assertThatThrownBy(() -> service.transition(new TransitionCommand(
                com.noticiasplaneta.news.domain.ArticleId.newId(),
                ArticleState.IN_REVIEW, chief, null)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("ARTICLE_NOT_FOUND");
    }
}
