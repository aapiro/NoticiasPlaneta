package com.noticiasplaneta.media.application.port.out;

import com.noticiasplaneta.media.application.port.in.ArticleMediaView;

import java.util.List;

public interface ArticleMediaRepository {

    void attach(String articleId, String mediaId, String role, int position, String caption);

    void detach(String articleId, String mediaId);

    List<ArticleMediaView> of(String articleId);

    List<ArticleMediaView> recent(int limit);

    /** Imagen principal de cada artículo, en una sola consulta. */
    java.util.Map<String, ArticleMediaView> leadsOf(java.util.Collection<String> articleIds);
}
