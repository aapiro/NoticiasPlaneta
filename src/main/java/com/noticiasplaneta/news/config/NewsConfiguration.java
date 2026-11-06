package com.noticiasplaneta.news.config;

import com.noticiasplaneta.news.application.port.in.CreateDraftFromItemUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionArticleUseCase;
import com.noticiasplaneta.news.application.port.in.GetArticleTextUseCase;
import com.noticiasplaneta.news.application.port.in.ListArticlesUseCase;
import com.noticiasplaneta.news.application.port.out.ArticleAdminQueryRepository;
import com.noticiasplaneta.news.application.port.out.ArticleQueryRepository;
import com.noticiasplaneta.news.application.port.in.EditArticleUseCase;
import com.noticiasplaneta.news.application.service.ArticleAdminQueryService;
import com.noticiasplaneta.news.application.service.EditArticleService;
import com.noticiasplaneta.news.application.service.ListArticleRevisionsService;
import com.noticiasplaneta.news.application.service.RestoreArticleRevisionService;
import com.noticiasplaneta.news.application.port.in.ListArticleRevisionsUseCase;
import com.noticiasplaneta.news.application.port.in.RestoreArticleRevisionUseCase;
import com.noticiasplaneta.news.application.port.out.ArticleRevisionRepository;
import com.noticiasplaneta.news.application.service.ArticleQueryService;
import com.noticiasplaneta.news.application.port.in.StoreArticleEmbeddingUseCase;
import com.noticiasplaneta.news.application.port.in.UnpublishArticleUseCase;
import com.noticiasplaneta.news.application.service.GetArticleTextService;
import com.noticiasplaneta.news.application.service.StoreArticleEmbeddingService;
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
    EditArticleUseCase editArticleUseCase(ArticleRepository articles, AuditPort audit,
                                          CachePurgePort cache, SearchIndexPort search,
                                          ArticleRevisionRepository revisions, Clock clock) {
        return new EditArticleService(articles, audit, cache, search, revisions, clock);
    }

    @Bean
    ListArticleRevisionsUseCase listArticleRevisionsUseCase(ArticleRevisionRepository revisions,
                                                            ArticleRepository articles) {
        return new ListArticleRevisionsService(revisions, articles);
    }

    @Bean
    RestoreArticleRevisionUseCase restoreArticleRevisionUseCase(
            ArticleRepository articles, ArticleRevisionRepository revisions,
            EditArticleUseCase editArticle, AuditPort audit, Clock clock) {
        return new RestoreArticleRevisionService(articles, revisions, editArticle, audit, clock);
    }

    @Bean
    ListArticlesUseCase listArticlesUseCase(ArticleAdminQueryRepository repository) {
        return new ArticleAdminQueryService(repository);
    }

    @Bean
    ArticleQueryService articleQueryService(ArticleQueryRepository queries, ArticleRepository articles) {
        return new ArticleQueryService(queries, articles);
    }

    @Bean
    GetArticleTextUseCase getArticleTextUseCase(ArticleRepository articles) {
        return new GetArticleTextService(articles);
    }

    @Bean
    StoreArticleEmbeddingUseCase storeArticleEmbeddingUseCase(ArticleRepository articles) {
        return new StoreArticleEmbeddingService(articles);
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
