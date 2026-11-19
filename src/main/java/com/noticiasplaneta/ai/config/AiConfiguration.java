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
import com.noticiasplaneta.ai.adapter.out.llm.GatedEnrichmentPort;
import com.noticiasplaneta.ai.application.port.out.AiCapabilities;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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

    /**
     * Puerta de capacidades (ADR-011). Envuelve al proveedor real para que, con la
     * capacidad apagada, no se le llame ni una vez.
     *
     * <p>Se marca {@code @Primary} y se excluye a sí misma del {@link ObjectProvider}
     * para no acabar envolviéndose en un bucle.
     */
    @Bean
    @Primary
    EnrichmentPort gatedEnrichmentPort(AiCapabilities capabilities, ObjectProvider<EnrichmentPort> ports) {
        EnrichmentPort delegate = ports.orderedStream()
                .filter(port -> !(port instanceof GatedEnrichmentPort))
                .findFirst()
                .orElseGet(UnconfiguredEnrichmentAdapter::new);
        return new GatedEnrichmentPort(capabilities, delegate);
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
