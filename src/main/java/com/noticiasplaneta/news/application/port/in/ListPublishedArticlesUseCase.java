package com.noticiasplaneta.news.application.port.in;

import java.util.List;

public interface ListPublishedArticlesUseCase {

    List<PublishedArticle> latest(String sectionId, int limit);
}
