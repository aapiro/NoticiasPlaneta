package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.UnpublishArticleUseCase;
import com.noticiasplaneta.news.application.port.in.UnpublishCommand;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.application.port.out.CachePurgePort;
import com.noticiasplaneta.news.application.port.out.SearchIndexPort;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.news.domain.UnpublishKind;
import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;
import com.noticiasplaneta.shared.domain.DomainException;
import com.noticiasplaneta.shared.domain.EventPublisher;

import java.time.Clock;

/**
 * F07 — Despublicación y corrección.
 *
 * <p>RN1: el contenido retirado no se borra jamás, se archiva. RN4: la URL retirada responde
 * 410 durante 90 días. RN5: el más mínimo fallo aquí es un problema legal, así que todo el
 * flujo es síncrono salvo la purga, que se pide de inmediato.
 */
public class UnpublishArticleService implements UnpublishArticleUseCase {

    public static final int MIN_REASON_LENGTH = 20;

    private final ArticleRepository articles;
    private final AuditPort audit;
    private final EventPublisher events;
    private final CachePurgePort cache;
    private final SearchIndexPort search;
    private final Clock clock;

    public UnpublishArticleService(ArticleRepository articles, AuditPort audit,
                                   EventPublisher events, CachePurgePort cache,
                                   SearchIndexPort search, Clock clock) {
        this.articles = articles;
        this.audit = audit;
        this.events = events;
        this.cache = cache;
        this.search = search;
        this.clock = clock;
    }

    @Override
    public Article unpublish(UnpublishCommand command) {
        Article article = articles.findById(command.articleId())
                .orElseThrow(() -> new DomainException("ARTICLE_NOT_FOUND",
                        "No existe el artículo " + command.articleId()));

        if (command.reason() == null || command.reason().trim().length() < MIN_REASON_LENGTH) {
            throw new DomainException("REASON_REQUIRED",
                    "El motivo es obligatorio y debe tener al menos " + MIN_REASON_LENGTH + " caracteres");
        }

        // Idempotente: si ya está archivado no se repite la purga ni el aviso.
        if (article.state() == ArticleState.ARCHIVED) {
            return article;
        }

        if (command.kind() == UnpublishKind.RETIRADA) {
            article.transitionTo(ArticleState.ARCHIVED, command.actor(), clock.instant());
            article.markUnpublished(command.reason(), clock.instant());
        } else {
            article.correct(command.reason(), command.kind(), clock.instant());
        }

        Article saved = articles.save(article);

        audit.record(new AuditEntry(
                command.actor().id(),
                command.actor().type().name(),
                "ARTICLE_" + command.kind().name(),
                "Article",
                saved.id().toString(),
                ArticleState.PUBLISHED.name(),
                saved.state().name(),
                clock.instant()));

        String url = "/" + saved.sectionId() + "/" + saved.slug().value();

        if (command.kind() == UnpublishKind.RETIRADA) {
            cache.purgeImmediately(url);
            search.remove(saved.id().toString());
        } else {
            cache.purgeEventually(url);
        }

        events.publish(new ArticleUnpublished(
                saved.id().value(), url, command.kind().name(), command.reason()));

        return saved;
    }
}
