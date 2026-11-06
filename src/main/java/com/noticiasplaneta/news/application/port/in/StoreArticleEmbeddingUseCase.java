package com.noticiasplaneta.news.application.port.in;

public interface StoreArticleEmbeddingUseCase {

    void store(String articleId, float[] embedding);
}
