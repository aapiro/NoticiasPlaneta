package com.noticiasplaneta.news.application.port.in;

import java.util.Optional;

public interface GetPublishedArticleUseCase {

    /** Artículo publicado por sección y slug. */
    Optional<PublishedArticle> bySectionAndSlug(String sectionId, String slug);

    Optional<ArticleText> textById(String id);

    /** Artículo retirado (archivado). Devuelve 410 en la web durante 90 días (RN4 de F07). */
    Optional<RetiredArticle> retiredBySectionAndSlug(String sectionId, String slug);
}
