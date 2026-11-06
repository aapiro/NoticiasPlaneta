package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.EditArticleCommand;
import com.noticiasplaneta.news.application.port.in.EditArticleResult;
import com.noticiasplaneta.news.application.port.in.EditArticleUseCase;
import com.noticiasplaneta.news.application.port.in.RestoreArticleRevisionUseCase;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.application.port.out.ArticleRevisionRepository;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.shared.domain.Actor;
import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;
import com.noticiasplaneta.shared.domain.DomainException;

import java.time.Clock;

/**
 * Restaurar una versión anterior.
 *
 * <p>No borra nada: restaurar es una edición más, así que la versión que había antes de
 * restaurar pasa a formar parte del historial y siempre se puede deshacer la restauración.
 */
public class RestoreArticleRevisionService implements RestoreArticleRevisionUseCase {

    private final ArticleRepository articles;
    private final ArticleRevisionRepository revisions;
    private final EditArticleUseCase editArticle;
    private final AuditPort audit;
    private final Clock clock;

    public RestoreArticleRevisionService(ArticleRepository articles,
                                         ArticleRevisionRepository revisions,
                                         EditArticleUseCase editArticle,
                                         AuditPort audit, Clock clock) {
        this.articles = articles;
        this.revisions = revisions;
        this.editArticle = editArticle;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public EditArticleResult restore(String articleId, int revision, Actor actor) {
        var articulo = articles.findById(ArticleId.of(articleId))
                .orElseThrow(() -> new DomainException("ARTICLE_NOT_FOUND",
                        "No existe el artículo " + articleId));

        var snapshot = revisions.byArticleAndRevision(articleId, revision)
                .orElseThrow(() -> new DomainException("REVISION_NOT_FOUND",
                        "No existe la versión " + revision));

        EditArticleResult resultado = editArticle.edit(new EditArticleCommand(
                articleId, snapshot.title(), snapshot.body(),
                articulo.revisionCount(), actor));

        audit.record(new AuditEntry(actor.id(), actor.type().name(), "ARTICLE_RESTORED",
                "Article", articleId, "v" + articulo.revisionCount(), "v" + revision,
                clock.instant()));

        return resultado;
    }
}
