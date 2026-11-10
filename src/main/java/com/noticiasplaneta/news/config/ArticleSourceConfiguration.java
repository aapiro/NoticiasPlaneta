package com.noticiasplaneta.news.config;

import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.application.port.out.ArticleSourceRepository;
import com.noticiasplaneta.news.application.port.out.UrlChecker;
import com.noticiasplaneta.news.application.service.ArticleSourceService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class ArticleSourceConfiguration {

    @Bean
    ArticleSourceService articleSourceService(ArticleRepository articles,
                                              ArticleSourceRepository sources,
                                              UrlChecker checker,
                                              Clock clock) {
        return new ArticleSourceService(articles, sources, checker, clock);
    }
}
