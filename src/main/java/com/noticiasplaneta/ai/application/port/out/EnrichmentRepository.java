package com.noticiasplaneta.ai.application.port.out;

import java.util.Optional;

public interface EnrichmentRepository {

    record Enrichment(java.util.List<String> tags, String summary, String model,
                      String promptVersion, String provider) {
    }

    void save(java.util.UUID articleId, Enrichment enrichment, String entitiesJson);

    Optional<Enrichment> findByArticle(java.util.UUID articleId);
}
