package com.noticiasplaneta.news.application.port.in;

/** Vista de una fuente para la API y la página pública. */
public record ArticleSourceView(
        String id,
        String url,
        String rootDomain,
        String kind,
        int httpStatus,
        boolean unreachable,
        int weight,
        String checkedAt) {
}
