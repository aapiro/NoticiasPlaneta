package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.Slug;

/** Resultado de crear un borrador. {@code created=false} cuando la operación fue idempotente. */
public record CreateDraftResult(ArticleId articleId, Slug slug, boolean created) {

    /**
     * Identificador y slug en texto, para que otros módulos usen el resultado sin depender
     * del dominio de noticias: entre módulos solo se cruza por {@code application.port.in}.
     */
    public String articleIdText() {
        return articleId == null ? null : articleId.toString();
    }

    public String slugText() {
        return slug == null ? null : slug.value();
    }
}
