package com.noticiasplaneta.news.application.port.out;

import java.util.List;
import java.util.Optional;

public interface ArticleRevisionRepository {

    record Snapshot(int revision, String title, String body, String editedBy, String editedAt,
                     String note) {
    }

    void save(String articleId, Snapshot snapshot);

    List<Snapshot> byArticle(String articleId);

    Optional<Snapshot> byArticleAndRevision(String articleId, int revision);
}
