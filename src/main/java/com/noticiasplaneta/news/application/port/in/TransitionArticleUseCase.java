package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.news.domain.Article;

public interface TransitionArticleUseCase {

    Article transition(TransitionCommand command);
}
