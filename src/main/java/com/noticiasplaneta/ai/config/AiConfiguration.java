package com.noticiasplaneta.ai.config;

import com.noticiasplaneta.ai.application.port.in.EnrichArticleUseCase;
import com.noticiasplaneta.ai.application.port.out.AiJobRepository;
import com.noticiasplaneta.ai.application.port.out.BudgetPort;
import com.noticiasplaneta.ai.application.port.out.EnrichmentPort;
import com.noticiasplaneta.ai.application.port.out.EnrichmentRepository;
import com.noticiasplaneta.ai.application.service.EnrichArticleService;
import com.noticiasplaneta.news.application.port.in.GetArticleTextUseCase;
import com.noticiasplaneta.news.application.port.in.StoreArticleEmbeddingUseCase;
import com.noticiasplaneta.shared.ai.EmbeddingPort;
import com.noticiasplaneta.ai.adapter.out.llm.UnconfiguredEnrichmentAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AiConfiguration {

    /**
     * Si no hay proveedor configurado, este adaptador falla con código claro en vez de
     * devolver un resultado vacío que parezca bueno.
     */
    @Bean
    @ConditionalOnMissingBean(EnrichmentPort.class)
    EnrichmentPort unconfiguredEnrichmentPort() {
        return new UnconfiguredEnrichmentAdapter();
    }

    @Bean
    EnrichArticleUseCase enrichArticleUseCase(AiJobRepository jobs,
                                              EnrichmentRepository enrichments,
                                              EnrichmentPort enricher,
                                              EmbeddingPort embeddings,
                                              BudgetPort budget,
                                              GetArticleTextUseCase articleText,
                                              StoreArticleEmbeddingUseCase storeEmbedding) {
        return new EnrichArticleService(jobs, enrichments, enricher, embeddings, budget,
                articleText, storeEmbedding);
    }
}
