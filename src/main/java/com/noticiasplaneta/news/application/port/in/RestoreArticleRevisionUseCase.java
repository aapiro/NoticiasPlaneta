package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.shared.domain.Actor;

public interface RestoreArticleRevisionUseCase {

    EditArticleResult restore(String articleId, int revision, Actor actor);
}
