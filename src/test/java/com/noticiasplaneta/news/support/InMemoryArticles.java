package com.noticiasplaneta.news.support;

import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.Slug;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryArticles implements ArticleRepository {

    private final Map<ArticleId, Article> data = new LinkedHashMap<>();

    @Override
    public Article save(Article article) {
        data.put(article.id(), article);
        return article;
    }

    @Override
    public Optional<Article> findById(ArticleId id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public boolean existsBySlug(String language, Slug slug) {
        return data.values().stream()
                .anyMatch(a -> a.language().equals(language) && a.slug().equals(slug));
    }

    @Override
    public boolean existsByIngestItemId(String ingestItemId) {
        return data.values().stream().anyMatch(a -> ingestItemId.equals(a.ingestItemId()));
    }

    @Override
    public Optional<Article> findByIngestItemId(String ingestItemId) {
        return data.values().stream().filter(a -> ingestItemId.equals(a.ingestItemId())).findFirst();
    }

    @Override
    public void saveEmbedding(ArticleId id, float[] embedding) {
        // no-op en memoria
    }

    @Override
    public long count() {
        return data.size();
    }
}
