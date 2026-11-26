package com.noticiasplaneta.news.application;

import com.noticiasplaneta.ingest.support.InMemoryEventPublisher;
import com.noticiasplaneta.news.application.port.in.PublishBatchUseCase.BatchPublishCommand;
import com.noticiasplaneta.news.application.service.PublishBatchService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("F5 · Caso de uso: publicar un lote")
class PublishBatchServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");
    private static final String ECONOMIA = "economia";

    private InMemoryArticles articles;
    private InMemoryAudit audit;
    private InMemoryEventPublisher events;
    private PublishBatchService service;

    private final Actor chief = Actor.human("u-chief", Set.of("CHIEF"), Set.of(ECONOMIA));
    private final Actor author = Actor.human("u-author", Set.of("AUTHOR"), Set.of(ECONOMIA));

    @BeforeEach
    void setUp() {
        articles = new InMemoryArticles();
        audit = new InMemoryAudit();
        events = new InMemoryEventPublisher();
        service = new PublishBatchService(articles, audit, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Article borrador(String titulo) {
        return articles.save(Article.draft("es", ECONOMIA, ArticleOrigin.MANUAL, null,
                Slug.fromTitle(titulo), titulo, "Cuerpo de " + titulo, NOW));
    }

    private List<String> ids(List<Article> articulos) {
        return articulos.stream().map(a -> a.id().value().toString()).toList();
    }

    @Test
    @DisplayName("Veinte borradores se publican y dejan una entrada de auditoría por artículo")
    void veinteDeUnaVez() {
        List<Article> borradores = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            borradores.add(borrador("Noticia número " + i));
        }

        var resultado = service.publish(new BatchPublishCommand(ids(borradores), chief));

        assertThat(resultado.published()).hasSize(20);
        assertThat(resultado.skipped()).isEmpty();
        assertThat(resultado.requested()).isEqualTo(20);

        assertThat(articles.findById(borradores.getFirst().id()).orElseThrow().state())
                .isEqualTo(ArticleState.PUBLISHED);

        assertThat(audit.entries()).hasSize(20);
        assertThat(audit.entries()).allSatisfy(entry -> {
            assertThat(entry.actorId()).isEqualTo("u-chief");
            assertThat(entry.action()).isEqualTo("ARTICLE_BATCH_PUBLISHED");
            assertThat(entry.before()).isEqualTo("DRAFT");
            assertThat(entry.after()).isEqualTo("PUBLISHED");
        });
        assertThat(audit.entries()).extracting(e -> e.entityId()).doesNotHaveDuplicates();

        assertThat(events.published()).hasSize(20)
                .allSatisfy(e -> assertThat(e).isInstanceOf(ArticleStateChanged.class));
    }

    @Test
    @DisplayName("Lo que no se puede publicar se omite con su motivo y no arrastra al resto")
    void omiteConMotivo() {
        Article bueno = borrador("Noticia publicable");
        Article yaPublicado = borrador("Noticia ya publicada");
        yaPublicado.publishNow(chief, NOW);

        var resultado = service.publish(new BatchPublishCommand(List.of(
                bueno.id().value().toString(),
                yaPublicado.id().value().toString(),
                "00000000-0000-0000-0000-000000000000",
                "no-es-un-uuid",
                bueno.id().value().toString()), chief));

        assertThat(resultado.requested()).as("los ids se deduplican").isEqualTo(4);
        assertThat(resultado.published()).hasSize(1);
        assertThat(resultado.skipped()).hasSize(3);
        assertThat(resultado.skipped()).extracting(
                        com.noticiasplaneta.news.application.port.in.PublishBatchUseCase.BatchPublishResult.Skipped::reason)
                .containsExactly(
                "El artículo ya está publicado",
                "No existe el artículo",
                "Identificador no válido");
        assertThat(audit.entries()).hasSize(1);
    }

    @Test
    @DisplayName("Un AUTHOR no publica nada: el lote se rechaza antes de empezar")
    void autorRechazado() {
        Article articulo = borrador("Noticia que un autor quiere publicar");

        assertThatThrownBy(() -> service.publish(new BatchPublishCommand(
                List.of(articulo.id().value().toString()), author)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("FORBIDDEN");

        assertThat(articles.findById(articulo.id()).orElseThrow().state()).isEqualTo(ArticleState.DRAFT);
        assertThat(audit.entries()).isEmpty();
    }

    @Test
    @DisplayName("Un lote por encima del tope se rechaza entero")
    void topeDeLote() {
        List<String> demasiados = new ArrayList<>();
        for (int i = 0; i <= PublishBatchService.MAX_IDS; i++) {
            demasiados.add(java.util.UUID.randomUUID().toString());
        }

        assertThatThrownBy(() -> service.publish(new BatchPublishCommand(demasiados, chief)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("BATCH_TOO_LARGE");
    }

    @Test
    @DisplayName("Un lote vacío no hace nada")
    void loteVacio() {
        var resultado = service.publish(new BatchPublishCommand(List.of(), chief));

        assertThat(resultado.isEmpty()).isTrue();
        assertThat(resultado.published()).isEmpty();
        assertThat(audit.entries()).isEmpty();
    }
}
