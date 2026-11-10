package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.PublishScheduledUseCase;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.application.port.out.OutboxEvent;
import com.noticiasplaneta.news.application.port.out.OutboxPort;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.shared.domain.Actor;
import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;
import com.noticiasplaneta.shared.domain.EventPublisher;
import com.noticiasplaneta.shared.events.ArticleStateChanged;

import java.time.Instant;
import java.util.UUID;

/**
 * F06 — publica lo que venció.
 *
 * <p>El estado y el evento de outbox se guardan en la misma transacción; la transacción
 * la abre el job (adaptador), no el servicio.
 */
public class PublishScheduledService implements PublishScheduledUseCase {

    private final ArticleRepository articles;
    private final AuditPort audit;
    private final EventPublisher events;
    private final OutboxPort outbox;

    public PublishScheduledService(ArticleRepository articles, AuditPort audit,
                                   EventPublisher events, OutboxPort outbox) {
        this.articles = articles;
        this.audit = audit;
        this.events = events;
        this.outbox = outbox;
    }

    @Override
    public boolean publishOne(ArticleId articleId, Instant now) {
        return articles.findById(articleId)
                .map(article -> publishIfDue(article, now))
                .orElse(false);
    }

    private boolean publishIfDue(Article article, Instant now) {
        if (article.state() != ArticleState.SCHEDULED) {
            return false;
        }
        Instant scheduledAt = article.scheduledAt();
        if (scheduledAt == null || scheduledAt.isAfter(now)) {
            return false;
        }

        ArticleState previous = article.state();
        article.transitionTo(ArticleState.PUBLISHED, Actor.system(), now);
        Article saved = articles.save(article);

        audit.record(new AuditEntry(
                "system",
                Actor.system().type().name(),
                "ARTICLE_PUBLISHED_SCHEDULED",
                "Article",
                saved.id().toString(),
                previous.name(),
                saved.state().name(),
                now));

        events.publish(new ArticleStateChanged(
                saved.id().value(), previous.name(), saved.state().name(), "system"));

        outbox.enqueue(new OutboxEvent(
                UUID.randomUUID(),
                saved.id().value(),
                "ArticlePublished.v1",
                publicUrl(saved),
                now));

        return true;
    }

    private static String publicUrl(Article article) {
        return "/" + article.sectionId() + "/" + article.slug().value();
    }
}
