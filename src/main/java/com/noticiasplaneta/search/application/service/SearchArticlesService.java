package com.noticiasplaneta.search.application.service;

import com.noticiasplaneta.search.application.port.in.SearchArticlesUseCase;
import com.noticiasplaneta.search.application.port.in.SearchHit;
import com.noticiasplaneta.search.application.port.out.ArticleSearchRepository;
import com.noticiasplaneta.search.application.port.out.EmbeddingPort;
import com.noticiasplaneta.shared.domain.DomainException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F10 — Búsqueda híbrida con Reciprocal Rank Fusion.
 *
 * <p>RN2: la fusión es RRF (k = 60), no una media ponderada inventada. RN3: si el modelo de
 * embeddings falla, se degrada a búsqueda léxica; nunca se devuelve un error al lector.
 */
public class SearchArticlesService implements SearchArticlesUseCase {

    static final int RRF_K = 60;

    private final ArticleSearchRepository repository;
    private final EmbeddingPort embeddings;

    public SearchArticlesService(ArticleSearchRepository repository, EmbeddingPort embeddings) {
        this.repository = repository;
        this.embeddings = embeddings;
    }

    @Override
    public List<SearchHit> search(String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            throw new DomainException("QUERY_TOO_SHORT", "La búsqueda necesita al menos 2 caracteres");
        }

        int candidates = Math.max(limit * 3, 20);
        List<SearchHit> lexical = repository.lexical(query, candidates);

        List<SearchHit> semantic;
        try {
            semantic = repository.semantic(embeddings.embed(query), candidates);
        } catch (RuntimeException e) {
            semantic = List.of();
        }

        return fuse(lexical, semantic, limit);
    }

    /** RRF: score = Σ 1 / (k + posición). Suma de dos rankings, sin pesos arbitrarios. */
    public static List<SearchHit> fuse(List<SearchHit> lexical, List<SearchHit> semantic, int limit) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, SearchHit> byId = new LinkedHashMap<>();

        accumulate(lexical, scores, byId);
        accumulate(semantic, scores, byId);

        List<SearchHit> result = new ArrayList<>();
        scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .forEach(entry -> {
                    SearchHit hit = byId.get(entry.getKey());
                    result.add(new SearchHit(hit.articleId(), hit.slug(), hit.sectionId(),
                            hit.title(), entry.getValue()));
                });
        return result;
    }

    private static void accumulate(List<SearchHit> hits, Map<String, Double> scores,
                                   Map<String, SearchHit> byId) {
        for (int i = 0; i < hits.size(); i++) {
            SearchHit hit = hits.get(i);
            byId.putIfAbsent(hit.articleId(), hit);
            scores.merge(hit.articleId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }
    }
}
