package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.news.domain.Actor;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.UnpublishKind;

public record UnpublishCommand(ArticleId articleId, UnpublishKind kind, String reason, Actor actor) {
}
