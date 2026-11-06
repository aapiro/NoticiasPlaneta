package com.noticiasplaneta.news.application;

import com.noticiasplaneta.ingest.support.InMemoryEventPublisher;
import com.noticiasplaneta.news.application.port.in.UnpublishCommand;
import com.noticiasplaneta.news.application.service.ArticleUnpublished;
import com.noticiasplaneta.news.application.service.UnpublishArticleService;
import com.noticiasplaneta.shared.domain.Actor;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleOrigin;
import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.news.domain.Slug;
import com.noticiasplaneta.news.domain.UnpublishKind;
import com.noticiasplaneta.news.support.InMemoryArticles;
import com.noticiasplaneta.news.support.InMemoryAudit;
import com.noticiasplaneta.news.support.RecordingCache;
import com.noticiasplaneta.news.support.RecordingSearch;
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

@DisplayName("F07 · Caso de uso: despublicar y corregir")
class UnpublishArticleServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");
    private static final String ECONOMIA = "economia";
    private static final String MOTIVO = "El dato de la fuente era incorrecto al publicar";

    private InMemoryArticles articles;
    private InMemoryAudit audit;
    private InMemoryEventPublisher events;
    private RecordingCache cache;
    private RecordingSearch search;
    private UnpublishArticleService service;
    private Article publishedArticle;

    private final Actor chief = Actor.human("u-chief", Set.of("CHIEF"), Set.of(ECONOMIA));
    private final Actor author = Actor.human("u-author", Set.of("AUTHOR"), Set.of(ECONOMIA));

    @BeforeEach
    void setUp() {
        articles = new InMemoryArticles();
        audit = new InMemoryAudit();
        events = new InMemoryEventPublisher();
        cache = new RecordingCache();
        search = new RecordingSearch();
        service = new UnpublishArticleService(articles, audit, events, cache, search,
                Clock.fixed(NOW, ZoneOffset.UTC));

        publishedArticle = Article.draft("es", ECONOMIA, ArticleOrigin.MANUAL, null,
                Slug.fromTitle("Reforma fiscal"), "Reforma fiscal", "Cuerpo de prueba", NOW);
        publishedArticle.transitionTo(ArticleState.IN_REVIEW, chief, NOW);
        publishedArticle.transitionTo(ArticleState.APPROVED, chief, NOW);
        publishedArticle.transitionTo(ArticleState.SCHEDULED, chief, NOW);
        publishedArticle.transitionTo(ArticleState.PUBLISHED, chief, NOW);
        articles.save(publishedArticle);
    }

    @Test
    @DisplayName("Retirar archiva, purga de inmediato, desindexa y audita")
    void retiradaCompleta() {
        Article result = service.unpublish(new UnpublishCommand(
                publishedArticle.id(), UnpublishKind.RETIRADA, MOTIVO, chief));

        assertThat(result.state()).isEqualTo(ArticleState.ARCHIVED);
        assertThat(result.unpublishedAt()).isEqualTo(NOW);
        assertThat(result.unpublishReason()).isEqualTo(MOTIVO);
        assertThat(cache.immediate()).containsExactly("/economia/reforma-fiscal");
        assertThat(search.removed()).containsExactly(publishedArticle.id().toString());
        assertThat(audit.entries()).hasSize(1);
        assertThat(audit.entries().getFirst().action()).isEqualTo("ARTICLE_RETIRADA");
        assertThat(events.published()).hasSize(1).first().isInstanceOf(ArticleUnpublished.class);
    }

    @Test
    @DisplayName("Sin motivo no se retira nada")
    void sinMotivo() {
        assertThatThrownBy(() -> service.unpublish(new UnpublishCommand(
                publishedArticle.id(), UnpublishKind.RETIRADA, "corto", chief)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("REASON_REQUIRED");

        assertThat(articles.findById(publishedArticle.id()).orElseThrow().state())
                .isEqualTo(ArticleState.PUBLISHED);
        assertThat(cache.immediate()).isEmpty();
    }

    @Test
    @DisplayName("Retirar dos veces es idempotente: no repurga ni reemite")
    void idempotente() {
        service.unpublish(new UnpublishCommand(
                publishedArticle.id(), UnpublishKind.RETIRADA, MOTIVO, chief));
        service.unpublish(new UnpublishCommand(
                publishedArticle.id(), UnpublishKind.RETIRADA, MOTIVO, chief));

        assertThat(cache.immediate()).hasSize(1);
        assertThat(events.published()).hasSize(1);
        assertThat(audit.entries()).hasSize(1);
    }

    @Test
    @DisplayName("Una corrección mantiene publicado y deja nota visible")
    void correccion() {
        Article result = service.unpublish(new UnpublishCommand(
                publishedArticle.id(), UnpublishKind.CORRECCION,
                "Se corrige la cifra de afiliación al dato oficial", chief));

        assertThat(result.state()).isEqualTo(ArticleState.PUBLISHED);
        assertThat(result.correctionNote()).startsWith("CORRECCION:");
        assertThat(cache.immediate()).isEmpty();
        assertThat(cache.eventual()).containsExactly("/economia/reforma-fiscal");
        assertThat(search.removed()).isEmpty();
    }

    @Test
    @DisplayName("Un artículo inexistente devuelve ARTICLE_NOT_FOUND")
    void inexistente() {
        assertThatThrownBy(() -> service.unpublish(new UnpublishCommand(
                com.noticiasplaneta.news.domain.ArticleId.newId(),
                UnpublishKind.RETIRADA, MOTIVO, chief)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("ARTICLE_NOT_FOUND");
    }
}
