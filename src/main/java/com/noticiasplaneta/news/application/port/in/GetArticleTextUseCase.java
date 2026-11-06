package com.noticiasplaneta.news.application.port.in;

import java.util.Optional;
import java.util.UUID;

public interface GetArticleTextUseCase {

    Optional<ArticleText> byId(UUID articleId);
}
