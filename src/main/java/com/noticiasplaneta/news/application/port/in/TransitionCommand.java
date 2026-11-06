package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.shared.domain.Actor;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.ArticleState;

public record TransitionCommand(ArticleId articleId, ArticleState target, Actor actor, String reason) {

    /** Construye el comando desde la entrada (formulario o JSON) sin exponer el dominio. */
    public static TransitionCommand of(String articleId, String target, Actor actor, String reason) {
        return new TransitionCommand(ArticleId.of(articleId), ArticleState.valueOf(target), actor, reason);
    }
}
