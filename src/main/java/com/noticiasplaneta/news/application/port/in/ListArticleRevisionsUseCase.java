package com.noticiasplaneta.news.application.port.in;

import java.util.List;

public interface ListArticleRevisionsUseCase {

    List<ArticleRevisionView> revisions(String articleId);
}
