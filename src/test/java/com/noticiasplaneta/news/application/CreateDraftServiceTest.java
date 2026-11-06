package com.noticiasplaneta.news.application;

import com.noticiasplaneta.ingest.support.InMemoryEventPublisher;
import com.noticiasplaneta.news.application.port.in.CreateDraftCommand;
import com.noticiasplaneta.news.application.port.in.CreateDraftResult;
import com.noticiasplaneta.news.application.service.ArticleDrafted;
import com.noticiasplaneta.news.application.service.CreateDraftService;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleOrigin;
import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.news.domain.Slug;
import com.noticiasplaneta.news.support.InMemoryArticles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("F02 · Caso de uso: crear borrador")
class CreateDraftServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");

    private InMemoryArticles articles;
    private InMemoryEventPublisher events;
    private CreateDraftService service;

    @BeforeEach
    void setUp() {
        articles = new InMemoryArticles();
        events = new InMemoryEventPublisher();
        service = new CreateDraftService(articles, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CreateDraftCommand command(String ingestItemId, String title) {
        return new CreateDraftCommand(ingestItemId, "es", "economia", title,
                "Cuerpo del artículo", "https://ejemplo.es/a", ArticleOrigin.INGEST);
    }

    @Test
    @DisplayName("Crea el artículo en DRAFT y emite ArticleDrafted")
    void creaBorrador() {
        CreateDraftResult result = service.create(command("item-1", "Reforma fiscal aprobada"));

        assertThat(result.created()).isTrue();
        assertThat(result.slug().value()).isEqualTo("reforma-fiscal-aprobada");
        assertThat(events.published()).hasSize(1).first().isInstanceOf(ArticleDrafted.class);

        Article saved = articles.findById(result.articleId()).orElseThrow();
        assertThat(saved.state()).isEqualTo(ArticleState.DRAFT);
        assertThat(saved.origin()).isEqualTo(ArticleOrigin.INGEST);
    }

    @Test
    @DisplayName("Es idempotente por ingestItemId")
    void idempotente() {
        CreateDraftResult first = service.create(command("item-1", "Titular uno"));
        CreateDraftResult second = service.create(command("item-1", "Titular uno"));

        assertThat(second.created()).isFalse();
        assertThat(second.articleId()).isEqualTo(first.articleId());
        assertThat(articles.count()).isEqualTo(1);
        assertThat(events.published()).hasSize(1);
    }

    @Test
    @DisplayName("Dos títulos iguales generan slugs distintos")
    void slugsUnicos() {
        CreateDraftResult a = service.create(command("item-1", "Reforma fiscal"));
        CreateDraftResult b = service.create(command("item-2", "Reforma fiscal"));

        assertThat(a.slug().value()).isEqualTo("reforma-fiscal");
        assertThat(b.slug().value()).isEqualTo("reforma-fiscal-2");
    }

    @Test
    @DisplayName("El origen del agente queda registrado")
    void origenAgente() {
        CreateDraftCommand agentCommand = new CreateDraftCommand("item-9", "es", "deportes",
                "Fichaje récord", "Cuerpo", "https://ejemplo.es/b", ArticleOrigin.AGENT);

        CreateDraftResult result = service.create(agentCommand);

        assertThat(articles.findById(result.articleId()).orElseThrow().origin())
                .isEqualTo(ArticleOrigin.AGENT);
    }
}
