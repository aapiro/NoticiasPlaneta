package com.noticiasplaneta.search.config;

import com.noticiasplaneta.search.application.port.in.SearchArticlesUseCase;
import com.noticiasplaneta.search.application.port.out.ArticleSearchRepository;
import com.noticiasplaneta.search.application.port.out.EmbeddingPort;
import com.noticiasplaneta.search.application.service.SearchArticlesService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SearchConfiguration {

    @Bean
    SearchArticlesUseCase searchArticlesUseCase(ArticleSearchRepository repository,
                                                EmbeddingPort embeddings) {
        return new SearchArticlesService(repository, embeddings);
    }
}
