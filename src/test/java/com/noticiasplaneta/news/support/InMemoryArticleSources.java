package com.noticiasplaneta.news.support;

import com.noticiasplaneta.news.application.port.out.ArticleSourceRepository;
import com.noticiasplaneta.news.domain.ArticleSource;
import com.noticiasplaneta.news.domain.ContrastLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryArticleSources implements ArticleSourceRepository {

    private final List<ArticleSource> data = new ArrayList<>();
    private final Map<UUID, ContrastSnapshot> contrasts = new HashMap<>();

    @Override
    public List<ArticleSource> findByArticleId(UUID articleId) {
        return data.stream().filter(source -> source.articleId().equals(articleId)).toList();
    }

    @Override
    public void save(ArticleSource source) {
        data.removeIf(existing -> existing.articleId().equals(source.articleId())
                && existing.url().equals(source.url()));
        data.add(source);
    }

    @Override
    public void updateContrast(UUID articleId, int independentSourceCount, ContrastLevel level) {
        contrasts.put(articleId, new ContrastSnapshot(independentSourceCount, level.name()));
    }

    @Override
    public Optional<ContrastSnapshot> contrast(UUID articleId) {
        return Optional.ofNullable(contrasts.get(articleId));
    }
}
