package com.noticiasplaneta.news.application.port.out;

import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.Slug;

import java.util.Optional;

public interface ArticleRepository {

    Article save(Article article);

    Optional<Article> findById(ArticleId id);

    boolean existsBySlug(String language, Slug slug);

    boolean existsByIngestItemId(String ingestItemId);

    Optional<Article> findByIngestItemId(String ingestItemId);

    long count();
}
