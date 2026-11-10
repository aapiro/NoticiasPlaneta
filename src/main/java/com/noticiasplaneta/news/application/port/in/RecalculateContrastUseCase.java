package com.noticiasplaneta.news.application.port.in;

/** F04: recalcula el contraste de un artículo. */
public interface RecalculateContrastUseCase {

    ArticleSourcesView recalculate(String articleId);
}
