package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.AutoPublishUseCase;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.application.port.out.AutoPublishStore;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * F6 — camino de escritura de la publicación automática.
 *
 * <p>No reimplementa reglas de selección: los candidatos vienen ya filtrados del
 * adaptador y el dominio vuelve a exigir las condiciones irreversibles (DRAFT, origen
 * de fuente pública) antes de cambiar el estado. Cada publicación deja tres rastros:
 * el evento de dominio (que dispara el enriquecido), la fila de outbox y la auditoría.
 *
 * <p>Estado, marca, auditoría y outbox van en la misma transacción: o se publica con su
 * marca, o no se publica.
 */
public class AutoPublishArticleService implements AutoPublishUseCase {

    /** Actor de la máquina: SYSTEM, que sí puede publicar; un AGENT no (RN5 de F05). */
    static final String AUTO_ACTOR = "system";
    static final String ACTION_CLASSIFIED = "ARTICLE_AUTO_CLASSIFIED";
    static final String ACTION_PUBLISHED = "ARTICLE_AUTO_PUBLISHED";

    private final ArticleRepository articles;
    private final AutoPublishStore store;
    private final AuditPort audit;
    private final EventPublisher events;
    private final OutboxPort outbox;
    private final org.springframework.transaction.support.TransactionOperations transaction;

    public AutoPublishArticleService(ArticleRepository articles, AutoPublishStore store,
                                     AuditPort audit, EventPublisher events, OutboxPort outbox,
                                     org.springframework.transaction.support.TransactionOperations transaction) {
        this.articles = articles;
        this.store = store;
        this.audit = audit;
        this.events = events;
        this.outbox = outbox;
        this.transaction = transaction;
    }

    @Override
    public List<Candidate> forClassification(ClassificationQuery query) {
        return store.candidatesForClassification(query.from(), query.to(), query.limit());
    }

    @Override
    public List<Candidate> forPublication(PublicationQuery query) {
        return store.candidatesForPublication(query.sectionId(), query.from(), query.to(),
                query.publishedSince(), query.limit());
    }

    @Override
    public boolean applyClassification(Classification classification, java.time.Instant now) {
        return Boolean.TRUE.equals(transaction.execute(status -> doApplyClassification(classification, now)));
    }

    private boolean doApplyClassification(Classification classification, java.time.Instant now) {
        Optional<Article> found = articles.findById(ArticleId.of(classification.articleId()));
        if (found.isEmpty() || found.get().state() != ArticleState.DRAFT) {
            return false;
        }
        Article article = found.get();
        String previous = article.sectionId();
        if (!previous.equals(classification.sectionId())) {
            article.assignSection(classification.sectionId());
            articles.save(article);
            audit.record(new AuditEntry(
                    AUTO_ACTOR, Actor.system().type().name(), ACTION_CLASSIFIED,
                    "Article", article.id().toString(),
                    previous, classification.sectionId(), now));
        }
        store.saveClassification(article.id().value(), classification.sectionId(),
                classification.model(), classification.promptVersion(), classification.confidence(), now);
        return true;
    }

    @Override
    public boolean publish(PublishRequest request) {
        return Boolean.TRUE.equals(transaction.execute(status -> doPublish(request)));
    }

    private boolean doPublish(PublishRequest request) {
        Optional<Article> found = articles.findById(ArticleId.of(request.articleId()));
        if (found.isEmpty() || found.get().state() != ArticleState.DRAFT) {
            return false;
        }
        Article article = found.get();
        if (!article.sectionId().equals(request.sectionId())) {
            return false;
        }

        article.publishAutomatically(request.now());
        Article saved = articles.save(article);

        if (!store.markAutoPublished(saved.id().value(), request.sectionId(), request.bias(),
                request.model(), request.promptVersion(), request.rules(), request.now())) {
            // Otra pasada se adelantó: se deshace el estado para no publicar sin marca.
            throw new IllegalStateException("AUTO_PUBLISH_RACE article=" + saved.id());
        }

        audit.record(new AuditEntry(
                AUTO_ACTOR, Actor.system().type().name(), ACTION_PUBLISHED,
                "Article", saved.id().toString(),
                ArticleState.DRAFT.name(),
                "PUBLISHED|automatica|seccion=" + request.sectionId()
                        + "|enfoque=" + request.bias()
                        + "|modelo=" + request.model()
                        + "|reglas=" + String.join(",", request.rules()),
                request.now()));

        events.publish(new ArticleStateChanged(
                saved.id().value(), ArticleState.DRAFT.name(), ArticleState.PUBLISHED.name(), AUTO_ACTOR));

        outbox.enqueue(new OutboxEvent(
                UUID.randomUUID(), saved.id().value(), "ArticlePublished.v1",
                "/" + saved.sectionId() + "/" + saved.slug().value(), request.now()));

        return true;
    }

    @Override
    public long publishedToday(String sectionId, java.time.Instant from, java.time.Instant to) {
        return store.countAutoPublished(sectionId, from, to);
    }
}
