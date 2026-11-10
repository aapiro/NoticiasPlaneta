package com.noticiasplaneta.news.application;

import com.noticiasplaneta.ingest.support.InMemoryEventPublisher;
import com.noticiasplaneta.news.application.port.in.SchedulePublishCommand;
import com.noticiasplaneta.news.application.service.SchedulePublishService;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleOrigin;
import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.news.domain.Slug;
import com.noticiasplaneta.news.support.InMemoryArticles;
import com.noticiasplaneta.news.support.InMemoryAudit;
import com.noticiasplaneta.shared.domain.Actor;
import com.noticiasplaneta.shared.domain.DomainException;
import com.noticiasplaneta.shared.events.ArticleStateChanged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("F06 · Caso de uso: programar publicación")
class SchedulePublishServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");
    private static final String ECONOMIA = "economia";

    private InMemoryArticles articles;
    private InMemoryAudit audit;
    private InMemoryEventPublisher events;
    private SchedulePublishService service;
    private Article article;

    private final Actor chief = Actor.human("u-chief", Set.of("CHIEF"), Set.of(ECONOMIA));

    @BeforeEach
    void setUp() {
        articles = new InMemoryArticles();
        audit = new InMemoryAudit();
        events = new InMemoryEventPublisher();
        service = new SchedulePublishService(articles, audit, events,
                Clock.fixed(NOW, ZoneOffset.UTC));

        article = Article.draft("es", ECONOMIA, ArticleOrigin.MANUAL, null,
                Slug.fromTitle("Reforma fiscal"), "Reforma fiscal", "Cuerpo de prueba", NOW);
        article.transitionTo(ArticleState.IN_REVIEW, chief, NOW);
        article.transitionTo(ArticleState.APPROVED, chief, NOW);
        articles.save(article);
    }

    @Test
    @DisplayName("Programa para una hora futura, audita y emite evento")
    void programa() {
        Instant publishAt = NOW.plusSeconds(3_600);

        Article result = service.schedule(
                new SchedulePublishCommand(article.id(), chief, publishAt, null));

        assertThat(result.state()).isEqualTo(ArticleState.SCHEDULED);
        assertThat(result.scheduledAt()).isEqualTo(publishAt);
        assertThat(audit.entries()).hasSize(1);
        assertThat(audit.entries().getFirst().action()).isEqualTo("ARTICLE_SCHEDULED");
        assertThat(audit.entries().getFirst().after()).isEqualTo("SCHEDULED");
        assertThat(events.published()).hasSize(1)
                .first().isInstanceOfSatisfying(ArticleStateChanged.class,
                        event -> {
                            assertThat(event.from()).isEqualTo("APPROVED");
                            assertThat(event.to()).isEqualTo("SCHEDULED");
                        });
    }

    @Test
    @DisplayName("Una hora pasada no se acepta")
    void horaPasada() {
        assertThatThrownBy(() -> service.schedule(
                new SchedulePublishCommand(article.id(), chief, NOW.minusSeconds(1), null)))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.code()).isEqualTo("PUBLISH_AT_INVALID"));

        assertThat(articles.findById(article.id()).orElseThrow().state())
                .isEqualTo(ArticleState.APPROVED);
    }
}
