package com.noticiasplaneta.ai.application;

import com.noticiasplaneta.ai.application.port.in.EnrichArticleUseCase;
import com.noticiasplaneta.ai.application.port.out.AiJobRepository;
import com.noticiasplaneta.ai.application.port.out.BudgetPort;
import com.noticiasplaneta.ai.application.port.out.EnrichmentPort;
import com.noticiasplaneta.ai.application.port.out.EnrichmentRepository;
import com.noticiasplaneta.ai.application.service.EnrichArticleService;
import com.noticiasplaneta.news.application.port.in.ArticleText;
import com.noticiasplaneta.news.application.port.in.GetArticleTextUseCase;
import com.noticiasplaneta.news.application.port.in.StoreArticleEmbeddingUseCase;
import com.noticiasplaneta.shared.ai.EmbeddingPort;
import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("F03 · Enriquecido asíncrono")
class EnrichArticleServiceTest {

    private static final UUID ARTICLE = UUID.randomUUID();
    private static final String TITLE = "El Congreso aprueba la reforma fiscal";
    private static final String BODY = "Pedro Sánchez defendió la medida en el hemiciclo.";

    private FakeJobs jobs;
    private FakeEnrichments enrichments;
    private FakeEmbeddings embeddings;
    private FakeBudget budget;
    private RecordingEmbeddingStore store;
    private EnrichArticleUseCase service;

    @BeforeEach
    void setUp() {
        jobs = new FakeJobs();
        enrichments = new FakeEnrichments();
        embeddings = new FakeEmbeddings();
        budget = new FakeBudget(true);
        store = new RecordingEmbeddingStore();

        GetArticleTextUseCase articleText = id -> Optional.of(
                new ArticleText(id, TITLE, BODY, "economia", "DRAFT", "INGEST", 1));

        service = new EnrichArticleService(jobs, enrichments, new FakeEnricher(), embeddings,
                budget, articleText, store);
    }

    @Test
    @DisplayName("Un artículo pendiente se enriquece y se marca como hecho")
    void enriquece() {
        jobs.enqueueIfAbsent(ARTICLE, "enrich");

        int done = service.processPending(10);

        assertThat(done).isEqualTo(1);
        assertThat(enrichments.saved).containsKey(ARTICLE);
        assertThat(jobs.state(ARTICLE)).isEqualTo("DONE");
        assertThat(store.stored).containsKey(ARTICLE.toString());
    }

    @Test
    @DisplayName("El enriquecido recibe el texto original y no lo modifica")
    void noTocaElTexto() {
        FakeEnricher enricher = new FakeEnricher();
        GetArticleTextUseCase articleText = id -> Optional.of(
                new ArticleText(id, TITLE, BODY, "economia", "DRAFT", "INGEST", 1));
        service = new EnrichArticleService(jobs, enrichments, enricher, embeddings, budget,
                articleText, store);

        jobs.enqueueIfAbsent(ARTICLE, "enrich");
        service.processPending(10);

        assertThat(enricher.lastTitle).isEqualTo(TITLE);
        assertThat(enricher.lastBody).isEqualTo(BODY);
    }

    @Test
    @DisplayName("Las entidades inventadas por el modelo se descartan")
    void descartaInventadas() {
        List<EnrichmentPort.Entity> filtradas = EnrichArticleService.dropInvented(
                List.of(new EnrichmentPort.Entity("Pedro Sánchez", "PERSON"),
                        new EnrichmentPort.Entity("Mariano Rajoy", "PERSON")),
                new ArticleText(ARTICLE, TITLE, BODY, "economia", "DRAFT", "INGEST", 1));

        assertThat(filtradas).extracting(EnrichmentPort.Entity::name).containsExactly("Pedro Sánchez");
    }

    @Test
    @DisplayName("Sin presupuesto no se llama al modelo y el job sigue pendiente")
    void sinPresupuesto() {
        budget = new FakeBudget(false);
        GetArticleTextUseCase articleText = id -> Optional.of(
                new ArticleText(id, TITLE, BODY, "economia", "DRAFT", "INGEST", 1));
        FakeEnricher enricher = new FakeEnricher();
        service = new EnrichArticleService(jobs, enrichments, enricher, embeddings, budget,
                articleText, store);

        jobs.enqueueIfAbsent(ARTICLE, "enrich");
        int done = service.processPending(10);

        assertThat(done).isZero();
        assertThat(enricher.called).isZero();
        assertThat(jobs.state(ARTICLE)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Si el modelo falla, el job se reintenta y no se guarda nada")
    void falloDelModelo() {
        GetArticleTextUseCase articleText = id -> Optional.of(
                new ArticleText(id, TITLE, BODY, "economia", "DRAFT", "INGEST", 1));
        EnrichmentPort roto = new EnrichmentPort() {
            @Override
            public EnrichmentResult enrich(String title, String body) {
                throw new DomainException("AI_NOT_CONFIGURED", "sin clave");
            }
        };
        service = new EnrichArticleService(jobs, enrichments, roto, embeddings, budget,
                articleText, store);

        jobs.enqueueIfAbsent(ARTICLE, "enrich");
        service.processPending(10);

        assertThat(enrichments.saved).isEmpty();
        assertThat(jobs.attempts(ARTICLE)).isEqualTo(1);
    }

    @Test
    @DisplayName("Encolar dos veces el mismo artículo no duplica el trabajo")
    void encolarEsIdempotente() {
        service.enqueue(ARTICLE);
        service.enqueue(ARTICLE);

        assertThat(jobs.count()).isEqualTo(1);
    }

    // ---------- dobles ----------

    static class FakeJobs implements AiJobRepository {
        private final Map<UUID, Job> jobs = new HashMap<>();
        private final Map<UUID, String> states = new HashMap<>();
        private final Map<UUID, Integer> attempts = new HashMap<>();

        @Override
        public void enqueueIfAbsent(UUID articleId, String type) {
            if (!jobs.containsKey(articleId)) {
                jobs.put(articleId, new Job(UUID.randomUUID(), articleId, type, "PENDING", 0));
                states.put(articleId, "PENDING");
                attempts.put(articleId, 0);
            }
        }

        @Override
        public List<Job> findPending(int max) {
            return new ArrayList<>(jobs.values());
        }

        @Override
        public void markDone(UUID id, long costMicros) {
            jobs.values().stream().filter(j -> j.id().equals(id)).findFirst()
                    .ifPresent(j -> states.put(j.articleId(), "DONE"));
        }

        @Override
        public void markFailed(UUID id, String error) {
            jobs.values().stream().filter(j -> j.id().equals(id)).findFirst()
                    .ifPresent(j -> attempts.merge(j.articleId(), 1, Integer::sum));
        }

        String state(UUID articleId) {
            return states.get(articleId);
        }

        int attempts(UUID articleId) {
            return attempts.getOrDefault(articleId, 0);
        }

        long count() {
            return jobs.size();
        }
    }

    static class FakeEnrichments implements EnrichmentRepository {
        final Map<UUID, Enrichment> saved = new HashMap<>();

        @Override
        public void save(UUID articleId, Enrichment enrichment, String entitiesJson) {
            saved.put(articleId, enrichment);
        }

        @Override
        public Optional<Enrichment> findByArticle(UUID articleId) {
            return Optional.ofNullable(saved.get(articleId));
        }
    }

    static class FakeEnricher implements EnrichmentPort {
        int called = 0;
        String lastTitle;
        String lastBody;

        @Override
        public EnrichmentResult enrich(String title, String body) {
            called++;
            lastTitle = title;
            lastBody = body;
            return new EnrichmentResult(List.of("fiscalidad", "congreso"),
                    List.of(new Entity("Pedro Sánchez", "PERSON")),
                    "El Congreso aprueba la reforma.", "deepseek-flash", "enrich-v1", "deepseek", 1_000);
        }
    }

    static class FakeEmbeddings implements EmbeddingPort {
        @Override
        public int dimensions() {
            return 4;
        }

        @Override
        public float[] embed(String text) {
            return new float[]{0.1f, 0.2f, 0.3f, 0.4f};
        }
    }

    static class RecordingEmbeddingStore implements StoreArticleEmbeddingUseCase {
        final Map<String, float[]> stored = new HashMap<>();

        @Override
        public void store(String articleId, float[] embedding) {
            stored.put(articleId, embedding);
        }
    }

    static class FakeBudget implements BudgetPort {
        private final boolean allowed;

        FakeBudget(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public boolean tryConsume(long costMicros) {
            return allowed;
        }
    }
}
