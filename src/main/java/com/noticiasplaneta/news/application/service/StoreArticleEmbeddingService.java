package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.StoreArticleEmbeddingUseCase;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.domain.ArticleId;

public class StoreArticleEmbeddingService implements StoreArticleEmbeddingUseCase {

    private final ArticleRepository articles;

    public StoreArticleEmbeddingService(ArticleRepository articles) {
        this.articles = articles;
    }

    @Override
    public void store(String articleId, float[] embedding) {
        articles.saveEmbedding(ArticleId.of(articleId), embedding);
    }
}
