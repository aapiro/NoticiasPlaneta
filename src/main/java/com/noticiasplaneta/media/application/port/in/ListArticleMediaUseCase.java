package com.noticiasplaneta.media.application.port.in;

import java.util.List;

public interface ListArticleMediaUseCase {

    List<ArticleMediaView> of(String articleId);

    /** Imagen principal de varios artículos, para la portada. */
    java.util.Map<String, ArticleMediaView> leadsOf(java.util.Collection<String> articleIds);

    /** Últimos medios subidos, para elegir desde el panel. */
    java.util.List<ArticleMediaView> recent(int limit);

    /** La imagen principal del artículo, si tiene. */
    default ArticleMediaView lead(String articleId) {
        return of(articleId).stream().filter(m -> "LEAD".equals(m.role())).findFirst().orElse(null);
    }
}
