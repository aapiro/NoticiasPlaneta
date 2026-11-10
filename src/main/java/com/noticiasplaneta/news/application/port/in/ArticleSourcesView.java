package com.noticiasplaneta.news.application.port.in;

import java.util.List;

/** Contraste persistido y fuentes de un artículo. */
public record ArticleSourcesView(
        String articleId,
        int independentSourceCount,
        String contrastLevel,
        List<ArticleSourceView> sources) {
}
