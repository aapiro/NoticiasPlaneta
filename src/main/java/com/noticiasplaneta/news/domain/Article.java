package com.noticiasplaneta.news.domain;

import com.noticiasplaneta.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * Agregado Article.
 *
 * <p>Invariantes: nace en DRAFT (RN1 de F02), el slug es único por idioma (ADR-001) y toda
 * transición pasa por {@link TransitionPolicy}.
 */
public final class Article {

    private final ArticleId id;
    private final String language;
    private final String sectionId;
    private final ArticleOrigin origin;
    private final String ingestItemId;
    private Slug slug;
    private String title;
    private ArticleState state;
    private int revisionCount;
    private Instant publishedAt;
    private Instant scheduledAt;
    private Instant createdAt;

    private Article(ArticleId id, String language, String sectionId, ArticleOrigin origin,
                    String ingestItemId, Slug slug, String title, ArticleState state,
                    int revisionCount, Instant publishedAt, Instant scheduledAt, Instant createdAt) {
        this.id = id;
        this.language = language;
        this.sectionId = sectionId;
        this.origin = origin;
        this.ingestItemId = ingestItemId;
        this.slug = slug;
        this.title = title;
        this.state = state;
        this.revisionCount = revisionCount;
        this.publishedAt = publishedAt;
        this.scheduledAt = scheduledAt;
        this.createdAt = createdAt;
    }

    public static Article draft(String language, String sectionId, ArticleOrigin origin,
                                String ingestItemId, Slug slug, String title, Instant now) {
        if (title == null || title.isBlank()) {
            throw new DomainException("ARTICLE_TITLE_REQUIRED", "El artículo necesita título");
        }
        if (language == null || language.isBlank()) {
            throw new DomainException("ARTICLE_LANGUAGE_REQUIRED", "El artículo necesita idioma");
        }
        if (sectionId == null || sectionId.isBlank()) {
            throw new DomainException("ARTICLE_SECTION_REQUIRED", "El artículo necesita sección");
        }
        Objects.requireNonNull(slug, "slug");
        return new Article(ArticleId.newId(), language, sectionId, origin, ingestItemId,
                slug, title.trim(), ArticleState.DRAFT, 1, null, null, now);
    }

    public static Article rehydrate(ArticleId id, String language, String sectionId, ArticleOrigin origin,
                                    String ingestItemId, Slug slug, String title, ArticleState state,
                                    int revisionCount, Instant publishedAt, Instant scheduledAt, Instant createdAt) {
        return new Article(id, language, sectionId, origin, ingestItemId, slug, title, state,
                revisionCount, publishedAt, scheduledAt, createdAt);
    }

    /** Aplica una transición autorizada. Devuelve el estado anterior. */
    public ArticleState transitionTo(ArticleState target, Actor actor, Instant now) {
        TransitionPolicy.assertCanPerform(actor, this.state, target, this.sectionId);
        ArticleState previous = this.state;
        this.state = target;
        if (target == ArticleState.PUBLISHED) {
            this.publishedAt = now;
        }
        if (target == ArticleState.SCHEDULED) {
            this.scheduledAt = now;
        }
        return previous;
    }

    public void rename(String newTitle) {
        if (newTitle == null || newTitle.isBlank()) {
            throw new DomainException("ARTICLE_TITLE_REQUIRED", "El artículo necesita título");
        }
        this.title = newTitle.trim();
        this.revisionCount++;
    }

    public void assignSlug(Slug slug) {
        this.slug = Objects.requireNonNull(slug, "slug");
    }

    public ArticleId id() { return id; }
    public String language() { return language; }
    public String sectionId() { return sectionId; }
    public ArticleOrigin origin() { return origin; }
    public String ingestItemId() { return ingestItemId; }
    public Slug slug() { return slug; }
    public String title() { return title; }
    public ArticleState state() { return state; }
    public int revisionCount() { return revisionCount; }
    public Instant publishedAt() { return publishedAt; }
    public Instant scheduledAt() { return scheduledAt; }
    public Instant createdAt() { return createdAt; }
}
