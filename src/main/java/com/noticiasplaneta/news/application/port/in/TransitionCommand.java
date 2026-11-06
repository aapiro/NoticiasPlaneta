package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.news.domain.Actor;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.ArticleState;

public record TransitionCommand(ArticleId articleId, ArticleState target, Actor actor, String reason) {
}
