package com.noticiasplaneta.news.application.port.in;

/** F04: vincula una fuente. */
public interface LinkSourceUseCase {

    ArticleSourcesView link(LinkSourceCommand command);
}
