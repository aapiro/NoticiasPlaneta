package com.noticiasplaneta.news.application.port.in;

/** F04: consulta las fuentes y el contraste de un artículo. */
public interface GetArticleSourcesUseCase {

    ArticleSourcesView get(String articleId);
}
