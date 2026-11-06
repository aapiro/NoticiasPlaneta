package com.noticiasplaneta.ai.application.service;

import com.noticiasplaneta.ai.application.port.in.EnrichArticleUseCase;
import com.noticiasplaneta.ai.application.port.out.AiJobRepository;
import com.noticiasplaneta.ai.application.port.out.BudgetPort;
import com.noticiasplaneta.ai.application.port.out.EnrichmentPort;
import com.noticiasplaneta.ai.application.port.out.EnrichmentRepository;
import com.noticiasplaneta.news.application.port.in.ArticleText;
import com.noticiasplaneta.news.application.port.in.GetArticleTextUseCase;
import com.noticiasplaneta.news.application.port.in.StoreArticleEmbeddingUseCase;
import com.noticiasplaneta.shared.ai.EmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * F03 — Enriquecido asíncrono.
 *
 * <p>RN1: nunca modifica el texto editorial. RN4: las entidades que el modelo inventa (no
 * aparecen literalmente en el artículo) se descartan. Nunca corre en la ruta del request.
 */
public class EnrichArticleService implements EnrichArticleUseCase {

    private static final Logger log = LoggerFactory.getLogger(EnrichArticleService.class);
    static final String TYPE = "enrich";
    /** Estimación conservadora por pieza; el coste real se registra después. */
    static final long ESTIMATED_COST_MICROS = 50_000;

    private final AiJobRepository jobs;
    private final EnrichmentRepository enrichments;
    private final EnrichmentPort enricher;
    private final EmbeddingPort embeddings;
    private final BudgetPort budget;
    private final GetArticleTextUseCase articleText;
    private final StoreArticleEmbeddingUseCase storeEmbedding;

    public EnrichArticleService(AiJobRepository jobs, EnrichmentRepository enrichments,
                                EnrichmentPort enricher, EmbeddingPort embeddings,
                                BudgetPort budget, GetArticleTextUseCase articleText,
                                StoreArticleEmbeddingUseCase storeEmbedding) {
        this.jobs = jobs;
        this.enrichments = enrichments;
        this.enricher = enricher;
        this.embeddings = embeddings;
        this.budget = budget;
        this.articleText = articleText;
        this.storeEmbedding = storeEmbedding;
    }

    @Override
    public void enqueue(UUID articleId) {
        jobs.enqueueIfAbsent(articleId, TYPE);
    }

    @Override
    public int processPending(int max) {
        int done = 0;
        for (AiJobRepository.Job job : jobs.findPending(max)) {
            if (process(job)) {
                done++;
            }
        }
        return done;
    }

    private boolean process(AiJobRepository.Job job) {
        Optional<ArticleText> maybe = articleText.byId(job.articleId());
        if (maybe.isEmpty()) {
            jobs.markFailed(job.id(), "ARTICLE_NOT_FOUND");
            return false;
        }
        ArticleText article = maybe.get();

        if (!budget.tryConsume(ESTIMATED_COST_MICROS)) {
            log.warn("Presupuesto de IA agotado, el job {} queda pendiente", job.id());
            return false;
        }

        EnrichmentPort.EnrichmentResult result;
        try {
            result = enricher.enrich(article.title(), article.body());
        } catch (com.noticiasplaneta.shared.domain.DomainException e) {
            // código de negocio (AI_NOT_CONFIGURED, AI_PROVIDER_ERROR...) en vez de la clase
            jobs.markFailed(job.id(), e.code());
            return false;
        } catch (RuntimeException e) {
            jobs.markFailed(job.id(), e.getClass().getSimpleName());
            return false;
        }

        List<String> tags = result.tags() == null ? List.of() : result.tags();

        enrichments.save(job.articleId(),
                new EnrichmentRepository.Enrichment(tags, result.summary(), result.model(),
                        result.promptVersion(), result.provider()),
                toJson(dropInvented(result.entities(), article)));

        try {
            storeEmbedding.store(article.articleId().toString(),
                    embeddings.embed(article.title() + ". " + article.body()));
        } catch (RuntimeException e) {
            log.warn("Sin embedding para {}: {}", article.articleId(), e.getMessage());
        }

        jobs.markDone(job.id(), result.costMicros());
        return true;
    }

    /** RN4: si la entidad no aparece literalmente en el texto, es una invención del modelo. */
    public static List<EnrichmentPort.Entity> dropInvented(List<EnrichmentPort.Entity> entities, ArticleText article) {
        if (entities == null) {
            return List.of();
        }
        String haystack = (article.title() + " " + article.body()).toLowerCase(Locale.ROOT);
        return entities.stream()
                .filter(e -> e.name() != null && haystack.contains(e.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static String toJson(List<EnrichmentPort.Entity> entities) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < entities.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"name\":\"").append(escape(entities.get(i).name()))
                    .append("\",\"type\":\"").append(escape(entities.get(i).type())).append("\"}");
        }
        return sb.append(']').toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}
