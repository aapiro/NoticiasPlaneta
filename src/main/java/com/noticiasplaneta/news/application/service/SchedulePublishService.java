package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.SchedulePublishCommand;
import com.noticiasplaneta.news.application.port.in.SchedulePublishUseCase;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;
import com.noticiasplaneta.shared.domain.DomainException;
import com.noticiasplaneta.shared.domain.EventPublisher;
import com.noticiasplaneta.shared.events.ArticleStateChanged;

import java.time.Clock;

/** F06 — programa la publicación y deja rastro en auditoría. */
public class SchedulePublishService implements SchedulePublishUseCase {

    private final ArticleRepository articles;
    private final AuditPort audit;
    private final EventPublisher events;
    private final Clock clock;

    public SchedulePublishService(ArticleRepository articles, AuditPort audit,
                                  EventPublisher events, Clock clock) {
        this.articles = articles;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public Article schedule(SchedulePublishCommand command) {
        Article article = articles.findById(command.articleId())
                .orElseThrow(() -> new DomainException("ARTICLE_NOT_FOUND",
                        "No existe el artículo " + command.articleId()));

        if (command.reason() != null && !command.reason().isBlank()
                && command.reason().length() < 20) {
            throw new DomainException("REASON_TOO_SHORT", "El motivo debe tener al menos 20 caracteres");
        }

        ArticleState previous = article.state();
        article.schedule(command.actor(), command.publishAt(), clock.instant());
        Article saved = articles.save(article);

        audit.record(new AuditEntry(
                command.actor().id(),
                command.actor().type().name(),
                "ARTICLE_SCHEDULED",
                "Article",
                saved.id().toString(),
                previous.name(),
                saved.state().name(),
                clock.instant()));

        events.publish(new ArticleStateChanged(
                saved.id().value(), previous.name(), saved.state().name(), command.actor().id()));

        return saved;
    }
}
