package com.noticiasplaneta.ai.adapter.out.persistence;

import com.noticiasplaneta.ai.application.port.out.EnrichmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class EnrichmentRepositoryAdapter implements EnrichmentRepository {

    private final SpringDataEnrichmentRepository jpa;
    private final Clock clock;

    EnrichmentRepositoryAdapter(SpringDataEnrichmentRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void save(UUID articleId, Enrichment enrichment, String entitiesJson) {
        ArticleEnrichmentJpaEntity e = jpa.findById(articleId).orElseGet(ArticleEnrichmentJpaEntity::new);
        e.articleId = articleId;
        e.tags = enrichment.tags().toArray(String[]::new);
        e.entities = entitiesJson;
        e.summary = enrichment.summary();
        e.model = enrichment.model();
        e.promptVersion = enrichment.promptVersion();
        e.provider = enrichment.provider();
        e.createdAt = clock.instant();
        jpa.save(e);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Enrichment> findByArticle(UUID articleId) {
        return jpa.findById(articleId).map(e -> new Enrichment(
                List.of(e.tags), e.summary, e.model, e.promptVersion, e.provider));
    }
}
