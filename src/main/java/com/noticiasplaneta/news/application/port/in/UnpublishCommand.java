package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.UnpublishKind;
import com.noticiasplaneta.shared.domain.Actor;

public record UnpublishCommand(ArticleId articleId, UnpublishKind kind, String reason, Actor actor) {

    /** Construye el comando desde la entrada (formulario o JSON) sin exponer el dominio. */
    public static UnpublishCommand of(String articleId, String kind, String reason, Actor actor) {
        return new UnpublishCommand(ArticleId.of(articleId), UnpublishKind.valueOf(kind), reason, actor);
    }
}
