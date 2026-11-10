package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.shared.domain.Actor;

import java.time.Instant;

/** F06: programar la publicación de un artículo aprobado. */
public record SchedulePublishCommand(ArticleId articleId, Actor actor, Instant publishAt, String reason) {

    /** Construye el comando desde la entrada (formulario o JSON) sin exponer ArticleId. */
    public static SchedulePublishCommand of(String articleId, Actor actor, Instant publishAt, String reason) {
        return new SchedulePublishCommand(ArticleId.of(articleId), actor, publishAt, reason);
    }
}
