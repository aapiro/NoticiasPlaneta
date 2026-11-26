package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.PublishBatchUseCase;
import com.noticiasplaneta.news.application.port.in.PublishBatchUseCase.BatchPublishResult.Published;
import com.noticiasplaneta.news.application.port.in.PublishBatchUseCase.BatchPublishResult.Skipped;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.news.domain.TransitionPolicy;
import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;
import com.noticiasplaneta.shared.domain.DomainException;
import com.noticiasplaneta.shared.domain.EventPublisher;
import com.noticiasplaneta.shared.events.ArticleStateChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * F5 — publica un lote de la bandeja.
 *
 * <p>Dos decisiones que no son de infraestructura y por eso están aquí (ver ADR-013):
 *
 * <ul>
 *   <li>Cada artículo se publica con {@link Article#publishNow}, que recorre la máquina de
 *       estados. No hay {@code update article set state='PUBLISHED'}.</li>
 *   <li>El lote no es una transacción única: un artículo bloqueado no arrastra a los demás.
 *       Se publica lo que se puede y se informa de lo que no, con el motivo.</li>
 * </ul>
 *
 * <p>Una entrada de auditoría por artículo (no una por paso interno ni un contador): el
 * asiento lleva el id afectado, el actor y el antes/después reales.
 */
public class PublishBatchService implements PublishBatchUseCase {

    private static final Logger log = LoggerFactory.getLogger(PublishBatchService.class);

    static final String ACCION = "ARTICLE_BATCH_PUBLISHED";

    private final ArticleRepository articles;
    private final AuditPort audit;
    private final EventPublisher events;
    private final Clock clock;

    public PublishBatchService(ArticleRepository articles, AuditPort audit,
                               EventPublisher events, Clock clock) {
        this.articles = articles;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public BatchPublishResult publish(BatchPublishCommand command) {
        // antes de tocar nada: si este actor no publica nunca, no se recorre la lista
        TransitionPolicy.assertCanPublish(command.actor());

        List<String> ids = distinct(command.articleIds());
        if (ids.size() > MAX_IDS) {
            throw new DomainException("BATCH_TOO_LARGE",
                    "El lote admite como máximo %d artículos y llegaron %d"
                            .formatted(MAX_IDS, ids.size()));
        }

        Instant now = clock.instant();
        List<Published> published = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();

        for (String raw : ids) {
            ArticleId id;
            try {
                id = ArticleId.of(raw);
            } catch (RuntimeException e) {
                skipped.add(new Skipped(raw, null, "Identificador no válido"));
                continue;
            }

            var encontrado = articles.findById(id);
            if (encontrado.isEmpty()) {
                skipped.add(new Skipped(id.toString(), null, "No existe el artículo"));
                continue;
            }

            Article article = encontrado.get();
            try {
                ArticleState previous = article.publishNow(command.actor(), now);
                Article saved = articles.save(article);

                audit.record(new AuditEntry(
                        command.actor().id(),
                        command.actor().type().name(),
                        ACCION,
                        "Article",
                        saved.id().toString(),
                        previous.name(),
                        saved.state().name(),
                        now));

                events.publish(new ArticleStateChanged(
                        saved.id().value(), previous.name(), saved.state().name(), command.actor().id()));

                published.add(new Published(saved.id().toString(), saved.title()));
            } catch (DomainException e) {
                skipped.add(new Skipped(id.toString(), article.title(), e.getMessage()));
            } catch (RuntimeException e) {
                // un fallo de persistencia no puede tirar el resumen del resto del lote
                log.warn("Lote: fallo al publicar {}: {}", id, e.toString());
                skipped.add(new Skipped(id.toString(), article.title(),
                        "Error al guardar: " + e.getClass().getSimpleName()));
            }
        }

        return new BatchPublishResult(ids.size(), List.copyOf(published), List.copyOf(skipped));
    }

    /** Un mismo id dos veces (formulario + «toda la página») no se publica dos veces. */
    private static List<String> distinct(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        LinkedHashSet<String> limpios = new LinkedHashSet<>();
        for (String id : raw) {
            if (id != null && !id.isBlank()) {
                limpios.add(id.trim());
            }
        }
        return List.copyOf(limpios);
    }
}
