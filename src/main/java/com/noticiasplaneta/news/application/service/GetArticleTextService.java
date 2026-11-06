package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.ArticleText;
import com.noticiasplaneta.news.application.port.in.GetArticleTextUseCase;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;

import java.util.Optional;
import java.util.UUID;

public class GetArticleTextService implements GetArticleTextUseCase {

    private final ArticleRepository articles;

    public GetArticleTextService(ArticleRepository articles) {
        this.articles = articles;
    }

    @Override
    public Optional<ArticleText> byId(UUID articleId) {
        return articles.findById(new com.noticiasplaneta.news.domain.ArticleId(articleId))
                .map(a -> new ArticleText(a.id().value(), a.title(), a.body(),
                        a.sectionId(), a.state().name(), a.origin().name(), a.revisionCount()));
    }
}
