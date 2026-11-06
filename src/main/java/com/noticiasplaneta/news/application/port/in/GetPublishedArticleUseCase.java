package com.noticiasplaneta.news.application.port.in;

import java.util.Optional;

public interface GetPublishedArticleUseCase {

    /** Artículo publicado por sección y slug. */
    Optional<PublishedArticle> bySectionAndSlug(String sectionId, String slug);

    Optional<ArticleText> textById(String id);
}
