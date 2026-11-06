package com.noticiasplaneta.news.config;

import com.noticiasplaneta.news.application.port.in.CreateDraftFromItemUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionArticleUseCase;
import com.noticiasplaneta.news.application.port.in.UnpublishArticleUseCase;
import com.noticiasplaneta.news.application.port.out.CachePurgePort;
import com.noticiasplaneta.news.application.port.out.SearchIndexPort;
import com.noticiasplaneta.news.application.service.UnpublishArticleService;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.application.service.CreateDraftService;
import com.noticiasplaneta.news.application.service.TransitionArticleService;
import com.noticiasplaneta.shared.domain.AuditPort;
import com.noticiasplaneta.shared.domain.EventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class NewsConfiguration {

    @Bean
    CreateDraftFromItemUseCase createDraftFromItemUseCase(ArticleRepository articles,
                                                          EventPublisher events,
                                                          Clock clock) {
        return new CreateDraftService(articles, events, clock);
    }

    @Bean
    UnpublishArticleUseCase unpublishArticleUseCase(ArticleRepository articles,
                                                    AuditPort audit,
                                                    EventPublisher events,
                                                    CachePurgePort cache,
                                                    SearchIndexPort search,
                                                    Clock clock) {
        return new UnpublishArticleService(articles, audit, events, cache, search, clock);
    }

    @Bean
    TransitionArticleUseCase transitionArticleUseCase(ArticleRepository articles,
                                                      AuditPort audit,
                                                      EventPublisher events,
                                                      Clock clock) {
        return new TransitionArticleService(articles, audit, events, clock);
    }
}
