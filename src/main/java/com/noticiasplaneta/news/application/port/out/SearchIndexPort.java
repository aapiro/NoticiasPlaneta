package com.noticiasplaneta.news.application.port.out;

public interface SearchIndexPort {

    void index(String articleId);

    void remove(String articleId);
}
