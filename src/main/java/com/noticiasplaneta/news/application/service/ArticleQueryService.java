package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.ArticleText;
import com.noticiasplaneta.news.application.port.in.FeedItem;
import com.noticiasplaneta.news.application.port.in.GetPublishedArticleUseCase;
import com.noticiasplaneta.news.application.port.in.ListPublishedArticlesUseCase;
import com.noticiasplaneta.news.application.port.in.ListPublishedArticlesUseCase.Page;
import com.noticiasplaneta.news.application.port.in.PublishedArticle;
import com.noticiasplaneta.news.application.port.in.RetiredArticle;
import com.noticiasplaneta.news.application.port.out.ArticleQueryRepository;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.domain.ArticleId;

import java.util.List;
import java.util.Optional;

/** Lecturas públicas. Solo artículos publicados: ni borradores ni archivados. */
public class ArticleQueryService implements ListPublishedArticlesUseCase, GetPublishedArticleUseCase {

    private final ArticleQueryRepository queries;
    private final ArticleRepository articles;

    public ArticleQueryService(ArticleQueryRepository queries, ArticleRepository articles) {
        this.queries = queries;
        this.articles = articles;
    }

    @Override
    public List<PublishedArticle> latest(String sectionId, int limit) {
        return queries.findPublished(sectionId, Math.clamp(limit, 1, 100));
    }

    @Override
    public List<FeedItem> feed(String sectionId, int limite) {
        return queries.findForFeed(sectionId, Math.clamp(limite, 1, 100));
    }

    @Override
    public Page paginar(String sectionId, int pagina, int tamano) {
        int p = Math.max(1, pagina);
        int t = Math.clamp(tamano, 1, 100);
        long total = queries.countPublished(sectionId);
        // Si piden una página más allá del final, se devuelve vacía en vez de la última:
        // mentir sobre la página rompe el canonical y el rel=next.
        int offset = (p - 1) * t;
        var lista = offset >= total && total > 0
                ? List.<PublishedArticle>of()
                : queries.findPublished(sectionId, t, offset);
        return new Page(lista, p, t, total);
    }

    @Override
    public Optional<PublishedArticle> bySectionAndSlug(String sectionId, String slug) {
        return queries.findPublishedBySectionAndSlug(sectionId, slug);
    }

    @Override
    public Optional<RetiredArticle> retiredBySectionAndSlug(String sectionId, String slug) {
        return queries.findRetiredBySectionAndSlug(sectionId, slug);
    }

    @Override
    public Optional<ArticleText> textById(String id) {
        return articles.findById(ArticleId.of(id))
                .filter(a -> a.state() == com.noticiasplaneta.news.domain.ArticleState.PUBLISHED)
                .map(a -> new ArticleText(a.id().value(), a.title(), a.body(), a.sectionId(),
                        a.state().name(), a.origin().name(), a.revisionCount()));
    }
}
