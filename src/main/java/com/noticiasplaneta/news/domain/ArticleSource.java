package com.noticiasplaneta.news.domain;

import java.time.Instant;
import java.util.UUID;

/** Una URL que respalda un artículo. */
public record ArticleSource(
        UUID id,
        UUID articleId,
        String url,
        String rootDomain,
        SourceKind kind,
        int httpStatus,
        Instant checkedAt,
        int weight,
        boolean unreachable) {
}
