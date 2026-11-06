package com.noticiasplaneta.media.application.service;

import com.noticiasplaneta.media.application.port.in.ArticleMediaView;
import com.noticiasplaneta.media.application.port.in.ListArticleMediaUseCase;
import com.noticiasplaneta.media.application.port.out.ArticleMediaRepository;

import java.util.List;

public class ListArticleMediaService implements ListArticleMediaUseCase {

    private final ArticleMediaRepository repository;

    public ListArticleMediaService(ArticleMediaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ArticleMediaView> of(String articleId) {
        return repository.of(articleId);
    }

    @Override
    public java.util.Map<String, ArticleMediaView> leadsOf(java.util.Collection<String> articleIds) {
        return repository.leadsOf(articleIds);
    }

    @Override
    public List<ArticleMediaView> recent(int limit) {
        return repository.recent(Math.clamp(limit, 1, 100));
    }
}
