package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.EditArticleCommand;
import com.noticiasplaneta.news.application.port.in.EditArticleResult;
import com.noticiasplaneta.news.application.port.in.EditArticleUseCase;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.application.port.out.ArticleRevisionRepository;
import com.noticiasplaneta.news.application.port.out.CachePurgePort;
import com.noticiasplaneta.news.application.port.out.SearchIndexPort;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;
import com.noticiasplaneta.shared.domain.DomainException;

import java.time.Clock;

/**
 * F13 — Editar titular y cuerpo desde el panel.
 *
 * <p>RN1: no hay reglas nuevas aquí; la validación y el control de versión viven en
 * {@link Article#edit}. Este servicio solo orquesta: cargar, editar, guardar, auditar y
 * refrescar lo que ya esté publicado.
 */
public class EditArticleService implements EditArticleUseCase {

    private final ArticleRepository articles;
    private final AuditPort audit;
    private final CachePurgePort cache;
    private final SearchIndexPort search;
    private final ArticleRevisionRepository revisions;
    private final Clock clock;

    public EditArticleService(ArticleRepository articles, AuditPort audit, CachePurgePort cache,
                              SearchIndexPort search, ArticleRevisionRepository revisions,
                              Clock clock) {
        this.articles = articles;
        this.audit = audit;
        this.cache = cache;
        this.search = search;
        this.revisions = revisions;
        this.clock = clock;
    }

    @Override
    public EditArticleResult edit(EditArticleCommand command) {
        Article article = articles.findById(ArticleId.of(command.articleId()))
                .orElseThrow(() -> new DomainException("ARTICLE_NOT_FOUND",
                        "No existe el artículo " + command.articleId()));

        String tituloAntes = article.title();
        String cuerpoAntes = article.body();
        int revisionAntes = article.revisionCount();

        // Se guarda cómo estaba ANTES de cambiarlo: así el historial siempre permite volver.
        if (!tituloAntes.equals(command.title() == null ? "" : command.title().trim())
                || !cuerpoAntes.equals(command.body() == null ? "" : command.body().trim())) {
            revisions.save(article.id().toString(), new ArticleRevisionRepository.Snapshot(
                    revisionAntes, tituloAntes, cuerpoAntes,
                    command.actor().id(), clock.instant().toString(), "edición"));
        }

        article.edit(command.title(), command.body(), command.expectedRevision());

        Article saved = articles.save(article);

        audit.record(new AuditEntry(
                command.actor().id(),
                command.actor().type().name(),
                "ARTICLE_EDITED",
                "Article",
                saved.id().toString(),
                "v" + revisionAntes + " · " + abreviar(tituloAntes),
                "v" + saved.revisionCount() + " · " + abreviar(saved.title()),
                clock.instant()));

        // Si ya está en la calle, el cambio se ve al instante: hay que refrescar caché e índice.
        String url = "/" + saved.sectionId() + "/" + saved.slug().value();
        if ("PUBLISHED".equals(saved.state().name())) {
            cache.purgeImmediately(url);
            search.index(saved.id().toString());
        } else {
            cache.purgeEventually(url);
        }

        return new EditArticleResult(saved.id().toString(), saved.slug().value(), saved.title(),
                saved.body(), saved.state().name(), saved.revisionCount());
    }

    private static String abreviar(String titulo) {
        return titulo.length() <= 60 ? titulo : titulo.substring(0, 57) + "…";
    }
}
