package com.noticiasplaneta.news.domain;

import com.noticiasplaneta.shared.domain.Actor;

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
    private String body;
    private ArticleState state;
    private int revisionCount;
    private Instant publishedAt;
    private Instant scheduledAt;
    private Instant createdAt;
    private Instant unpublishedAt;
    private String unpublishReason;
    private String correctionNote;

    private Article(ArticleId id, String language, String sectionId, ArticleOrigin origin,
                    String ingestItemId, Slug slug, String title, String body, ArticleState state,
                    int revisionCount, Instant publishedAt, Instant scheduledAt, Instant createdAt,
                    Instant unpublishedAt, String unpublishReason, String correctionNote) {
        this.id = id;
        this.language = language;
        this.sectionId = sectionId;
        this.origin = origin;
        this.ingestItemId = ingestItemId;
        this.slug = slug;
        this.title = title;
        this.body = body;
        this.state = state;
        this.revisionCount = revisionCount;
        this.publishedAt = publishedAt;
        this.scheduledAt = scheduledAt;
        this.createdAt = createdAt;
        this.unpublishedAt = unpublishedAt;
        this.unpublishReason = unpublishReason;
        this.correctionNote = correctionNote;
    }

    public static Article draft(String language, String sectionId, ArticleOrigin origin,
                                String ingestItemId, Slug slug, String title, String body, Instant now) {
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
                slug, title.trim(), body == null ? "" : body, ArticleState.DRAFT, 1,
                null, null, now, null, null, null);
    }

    public static Article rehydrate(ArticleId id, String language, String sectionId, ArticleOrigin origin,
                                    String ingestItemId, Slug slug, String title, String body,
                                    ArticleState state,
                                    int revisionCount, Instant publishedAt, Instant scheduledAt, Instant createdAt,
                                    Instant unpublishedAt, String unpublishReason, String correctionNote) {
        return new Article(id, language, sectionId, origin, ingestItemId, slug, title, body, state,
                revisionCount, publishedAt, scheduledAt, createdAt,
                unpublishedAt, unpublishReason, correctionNote);
    }

    /** F07: retirada. El contenido no se borra, se archiva con su motivo. */
    public void markUnpublished(String reason, Instant now) {
        if (reason == null || reason.isBlank()) {
            throw new DomainException("REASON_REQUIRED", "Una retirada exige motivo");
        }
        this.unpublishedAt = now;
        this.unpublishReason = reason.trim();
    }

    /** F07: corrección o rectificación. El artículo sigue publicado, con nota visible. */
    public void correct(String note, UnpublishKind kind, Instant now) {
        if (note == null || note.isBlank()) {
            throw new DomainException("REASON_REQUIRED", "Una corrección exige nota");
        }
        if (this.state != ArticleState.PUBLISHED) {
            throw new DomainException("CORRECTION_NOT_PUBLISHED",
                    "Solo se corrige lo que está publicado");
        }
        this.correctionNote = kind.name() + ": " + note.trim();
        this.revisionCount++;
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

    /**
     * Edita titular y cuerpo (F13).
     *
     * <p>El slug NO cambia nunca: es la URL canónica (ADR-001). Cambiar el titular no mueve
     * la noticia de sitio ni rompe los enlaces entrantes.
     *
     * @param expectedRevision versión que el editor tenía delante; si no coincide, otra persona
     *                         guardó antes y se rechaza en vez de pisar su trabajo
     */
    public void edit(String newTitle, String newBody, int expectedRevision) {
        if (this.revisionCount != expectedRevision) {
            throw new DomainException("CONCURRENT_EDIT",
                    "El artículo ha cambiado mientras lo editabas (versión %d, esperabas %d)"
                            .formatted(this.revisionCount, expectedRevision));
        }
        if (newTitle == null || newTitle.isBlank()) {
            throw new DomainException("ARTICLE_TITLE_REQUIRED", "El artículo necesita título");
        }
        if (newBody == null || newBody.isBlank()) {
            throw new DomainException("ARTICLE_BODY_REQUIRED", "El artículo necesita cuerpo");
        }
        this.title = newTitle.trim();
        this.body = newBody.trim();
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
    public String body() { return body; }
    public ArticleState state() { return state; }
    public int revisionCount() { return revisionCount; }
    public Instant publishedAt() { return publishedAt; }
    public Instant scheduledAt() { return scheduledAt; }
    public Instant createdAt() { return createdAt; }
    public Instant unpublishedAt() { return unpublishedAt; }
    public String unpublishReason() { return unpublishReason; }
    public String correctionNote() { return correctionNote; }
}
