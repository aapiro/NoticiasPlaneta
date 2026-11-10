package com.noticiasplaneta.news.support;

import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.news.domain.Slug;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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

    @Override
    public List<ArticleId> findScheduledDue(Instant now, int limit) {
        return data.values().stream()
                .filter(article -> article.state() == ArticleState.SCHEDULED)
                .filter(article -> article.scheduledAt() != null
                        && !article.scheduledAt().isAfter(now))
                .sorted(Comparator.comparing(Article::scheduledAt))
                .limit(limit)
                .map(Article::id)
                .toList();
    }
}
