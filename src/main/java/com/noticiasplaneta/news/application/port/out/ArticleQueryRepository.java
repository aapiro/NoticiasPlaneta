package com.noticiasplaneta.news.application.port.out;

import com.noticiasplaneta.news.application.port.in.PublishedArticle;

import java.util.List;
import java.util.Optional;

public interface ArticleQueryRepository {

    List<PublishedArticle> findPublished(String sectionId, int limit);

    Optional<PublishedArticle> findPublishedBySectionAndSlug(String sectionId, String slug);

    Optional<com.noticiasplaneta.news.application.port.in.RetiredArticle> findRetiredBySectionAndSlug(
            String sectionId, String slug);
}
