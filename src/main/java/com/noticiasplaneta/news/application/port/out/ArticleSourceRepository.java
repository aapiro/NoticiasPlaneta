package com.noticiasplaneta.news.application.port.out;

import com.noticiasplaneta.news.domain.ArticleSource;
import com.noticiasplaneta.news.domain.ContrastLevel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia de fuentes y del contraste calculado. */
public interface ArticleSourceRepository {

    List<ArticleSource> findByArticleId(UUID articleId);

    void save(ArticleSource source);

    void updateContrast(UUID articleId, int independentSourceCount, ContrastLevel level);

    Optional<ContrastSnapshot> contrast(UUID articleId);

    record ContrastSnapshot(int independentSourceCount, String contrastLevel) {
    }
}
