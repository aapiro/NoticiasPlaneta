package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.news.domain.Article;

public interface UnpublishArticleUseCase {

    Article unpublish(UnpublishCommand command);
}
