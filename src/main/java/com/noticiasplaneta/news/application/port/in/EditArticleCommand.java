package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.shared.domain.Actor;

/** Edición de titular y cuerpo desde el panel (F13). */
public record EditArticleCommand(String articleId, String title, String body,
                                 int expectedRevision, Actor actor) {
}
