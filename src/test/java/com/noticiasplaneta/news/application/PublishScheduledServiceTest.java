package com.noticiasplaneta.news.application;

import com.noticiasplaneta.ingest.support.InMemoryEventPublisher;
import com.noticiasplaneta.news.application.service.PublishScheduledService;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleOrigin;
import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.news.domain.Slug;
import com.noticiasplaneta.news.support.InMemoryArticles;
import com.noticiasplaneta.news.support.InMemoryAudit;
import com.noticiasplaneta.news.support.InMemoryOutbox;
import com.noticiasplaneta.shared.domain.Actor;
import com.noticiasplaneta.shared.events.ArticleStateChanged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("F06 · Caso de uso: publicar lo programado")
class PublishScheduledServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");
    private static final String ECONOMIA = "economia";

    private InMemoryArticles articles;
    private InMemoryAudit audit;
    private InMemoryEventPublisher events;
    private InMemoryOutbox outbox;
    private PublishScheduledService service;
    private Article article;

    private final Actor chief = Actor.human("u-chief", Set.of("CHIEF"), Set.of(ECONOMIA));

    @BeforeEach
    void setUp() {
        articles = new InMemoryArticles();
        audit = new InMemoryAudit();
        events = new InMemoryEventPublisher();
        outbox = new InMemoryOutbox();
        service = new PublishScheduledService(articles, audit, events, outbox);

        article = Article.draft("es", ECONOMIA, ArticleOrigin.MANUAL, null,
                Slug.fromTitle("Reforma fiscal"), "Reforma fiscal", "Cuerpo de prueba", NOW);
        article.transitionTo(ArticleState.IN_REVIEW, chief, NOW);
        article.transitionTo(ArticleState.APPROVED, chief, NOW);
        article.transitionTo(ArticleState.SCHEDULED, chief, NOW);
        articles.save(article);
    }

    @Test
    @DisplayName("Publica lo vencido, audita, emite evento y encola outbox")
    void publicaVencido() {
        Instant publishTime = NOW.plusSeconds(60);

        boolean published = service.publishOne(article.id(), publishTime);

        assertThat(published).isTrue();
        Article saved = articles.findById(article.id()).orElseThrow();
        assertThat(saved.state()).isEqualTo(ArticleState.PUBLISHED);
        assertThat(saved.publishedAt()).isEqualTo(publishTime);
        assertThat(audit.entries()).hasSize(1);
        assertThat(audit.entries().getFirst().action()).isEqualTo("ARTICLE_PUBLISHED_SCHEDULED");
        assertThat(audit.entries().getFirst().actorType()).isEqualTo("SYSTEM");
        assertThat(events.published()).hasSize(1)
                .first().isInstanceOfSatisfying(ArticleStateChanged.class,
                        event -> {
                            assertThat(event.from()).isEqualTo("SCHEDULED");
                            assertThat(event.to()).isEqualTo("PUBLISHED");
                        });
        assertThat(outbox.pending()).hasSize(1);
        assertThat(outbox.pending().getFirst().url()).isEqualTo("/economia/reforma-fiscal");
        assertThat(outbox.pending().getFirst().eventType()).isEqualTo("ArticlePublished.v1");
    }

    @Test
    @DisplayName("No publica si todavía no ha vencido")
    void noPublicaAntesDeTiempo() {
        boolean published = service.publishOne(article.id(), NOW.minusSeconds(1));

        assertThat(published).isFalse();
        assertThat(articles.findById(article.id()).orElseThrow().state())
                .isEqualTo(ArticleState.SCHEDULED);
        assertThat(outbox.pending()).isEmpty();
    }

    @Test
    @DisplayName("No publica dos veces el mismo artículo")
    void noDuplica() {
        service.publishOne(article.id(), NOW.plusSeconds(60));

        boolean second = service.publishOne(article.id(), NOW.plusSeconds(120));

        assertThat(second).isFalse();
        assertThat(outbox.pending()).hasSize(1);
    }
}
