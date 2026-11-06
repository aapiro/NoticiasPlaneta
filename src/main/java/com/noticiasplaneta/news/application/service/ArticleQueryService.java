package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.ArticleText;
import com.noticiasplaneta.news.application.port.in.GetPublishedArticleUseCase;
import com.noticiasplaneta.news.application.port.in.ListPublishedArticlesUseCase;
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
