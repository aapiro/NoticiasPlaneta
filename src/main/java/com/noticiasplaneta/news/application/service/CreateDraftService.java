package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.CreateDraftCommand;
import com.noticiasplaneta.news.application.port.in.CreateDraftFromItemUseCase;
import com.noticiasplaneta.news.application.port.in.CreateDraftResult;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.news.domain.Slug;
import com.noticiasplaneta.shared.domain.DomainException;
import com.noticiasplaneta.shared.domain.EventPublisher;

import java.time.Clock;
import java.util.Optional;

/**
 * F02 — Creación de borrador desde un elemento de ingesta.
 *
 * <p>RN1: nace siempre en DRAFT. RN2: la primera revisión es inmutable (se guarda aparte).
 * Idempotente por {@code ingestItemId} (E01).
 */
public class CreateDraftService implements CreateDraftFromItemUseCase {

    private static final int MAX_SLUG_ATTEMPTS = 50;

    private final ArticleRepository articles;
    private final EventPublisher events;
    private final Clock clock;

    public CreateDraftService(ArticleRepository articles, EventPublisher events, Clock clock) {
        this.articles = articles;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public CreateDraftResult create(CreateDraftCommand command) {
        if (command.ingestItemId() != null) {
            Optional<Article> existing = articles.findByIngestItemId(command.ingestItemId());
            if (existing.isPresent()) {
                Article article = existing.get();
                return new CreateDraftResult(article.id(), article.slug(), false);
            }
        }

        Slug slug = allocateSlug(command.language(), Slug.fromTitle(command.title()));

        Article article = Article.draft(
                command.language(),
                command.sectionId(),
                command.origin(),
                command.ingestItemId(),
                slug,
                command.title(),
                command.body(),
                clock.instant());

        Article saved = articles.save(article);

        events.publish(new com.noticiasplaneta.shared.events.ArticleDrafted(
                saved.id().value(), saved.language(), saved.sectionId(), saved.origin().name()));

        return new CreateDraftResult(saved.id(), saved.slug(), true);
    }

    private Slug allocateSlug(String language, Slug candidate) {
        Slug current = candidate;
        for (int attempt = 2; attempt <= MAX_SLUG_ATTEMPTS; attempt++) {
            if (!articles.existsBySlug(language, current)) {
                return current;
            }
            current = candidate.withSuffix(attempt);
        }
        throw new DomainException("SLUG_EXHAUSTED", "No se ha podido asignar slug único para " + candidate);
    }
}
