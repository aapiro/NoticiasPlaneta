package com.noticiasplaneta.news.application.port.out;

import com.noticiasplaneta.news.application.port.in.FeedItem;
import com.noticiasplaneta.news.application.port.in.PublishedArticle;

import java.util.List;
import java.util.Optional;

public interface ArticleQueryRepository {

    List<PublishedArticle> findPublished(String sectionId, int limit);

    /** Página de publicadas, para la portada y las secciones (backlog #3). */
    List<PublishedArticle> findPublished(String sectionId, int limit, int offset);

    /** Total de publicadas de la sección (o del sitio si {@code sectionId} es nulo). */
    long countPublished(String sectionId);

    /** Últimas publicadas con su cuerpo, para el RSS de salida (backlog #5). */
    List<FeedItem> findForFeed(String sectionId, int limit);

    Optional<PublishedArticle> findPublishedBySectionAndSlug(String sectionId, String slug);

    Optional<com.noticiasplaneta.news.application.port.in.RetiredArticle> findRetiredBySectionAndSlug(
            String sectionId, String slug);
}
