package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.Slug;

/** Resultado de crear un borrador. {@code created=false} cuando la operación fue idempotente. */
public record CreateDraftResult(ArticleId articleId, Slug slug, boolean created) {
}
