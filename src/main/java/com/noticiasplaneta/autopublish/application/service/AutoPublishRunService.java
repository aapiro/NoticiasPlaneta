package com.noticiasplaneta.autopublish.application.service;

import com.noticiasplaneta.ai.application.port.in.ClassifyArticleUseCase;
import com.noticiasplaneta.ai.application.port.in.PublishAutoGate;
import com.noticiasplaneta.autopublish.application.port.in.AutoPublishRunUseCase;
import com.noticiasplaneta.autopublish.domain.AutoPublishRules;
import com.noticiasplaneta.news.application.port.in.AutoPublishUseCase;
import com.noticiasplaneta.news.application.port.in.AutoPublishUseCase.Candidate;
import com.noticiasplaneta.news.application.port.in.AutoPublishUseCase.ClassificationQuery;
import com.noticiasplaneta.news.application.port.in.AutoPublishUseCase.PublicationQuery;
import com.noticiasplaneta.news.application.port.in.AutoPublishUseCase.PublishRequest;
import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase.SectionView;
import com.noticiasplaneta.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * F6 — la pasada completa: clasificar y publicar.
 *
 * <p>Orden deliberado: primero clasificación (una vez por pieza, con traza en BD) y
 * después publicación (sin modelo de por medio, solo reglas). Consecuencias:
 * <ul>
 *   <li>Si el proveedor de IA falla, lo ya clasificado se sigue publicando.</li>
 *   <li>Si el interruptor está apagado, no se clasifica nada y no se gasta un céntimo.</li>
 *   <li>Una sección con cupo agotado se salta sin tocar las demás.</li>
 * </ul>
 */
public class AutoPublishRunService implements AutoPublishRunUseCase {

    private static final Logger log = LoggerFactory.getLogger(AutoPublishRunService.class);

    /** Día natural del periódico: el cupo se cuenta en hora de Madrid. */
    static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    /** Sección a la que cae lo que el modelo no ve claro. Es la semilla del portal. */
    static final String FALLBACK_SECTION = "general";

    private final AutoPublishUseCase news;
    private final SectionAdminUseCase sections;
    private final ClassifyArticleUseCase classifier;
    private final PublishAutoGate gate;
    private final Clock clock;
    private final int batchSize;

    private volatile Report last = new Report(Instant.EPOCH, "sin_ejecutar", 0, 0, 0, List.of());

    public AutoPublishRunService(AutoPublishUseCase news, SectionAdminUseCase sections,
                                 ClassifyArticleUseCase classifier, PublishAutoGate gate,
                                 Clock clock, int batchSize) {
        this.news = news;
        this.sections = sections;
        this.classifier = classifier;
        this.gate = gate;
        this.clock = clock;
        this.batchSize = Math.clamp(batchSize, 1, 100);
    }

    @Override
    public Report runNow() {
        Instant now = clock.instant();
        if (!gate.enabled()) {
            last = Report.off(now);
            return last;
        }

        List<SectionView> activas = sections.sections().stream().filter(SectionView::activa).toList();
        List<String> catalogo = activas.stream().map(SectionView::slug).toList();
        List<String> notes = new ArrayList<>();

        int classified = classifyPending(now, catalogo, notes);
        int published = 0;
        int skipped = 0;

        for (SectionView section : activas) {
            if (!section.autoPublica()) continue;
            long hoy = news.publishedToday(section.slug(), dayStart(now), dayEnd(now));
            if (!AutoPublishRules.quotaAvailable(hoy, section.autoLimiteDiario())) {
                notes.add("cupo_agotado:" + section.slug() + "(" + hoy + "/" + section.autoLimiteDiario() + ")");
                continue;
            }
            int cupo = (int) Math.min(section.autoLimiteDiario() - hoy, batchSize);
            List<Candidate> listos = news.forPublication(new PublicationQuery(
                    section.slug(), now.minus(AutoPublishRules.WINDOW), now,
                    now.minus(AutoPublishRules.REPEAT_WINDOW), cupo));

            for (Candidate candidate : listos) {
                if (!AutoPublishRules.withinWindow(candidate.sourceDate(), now)) {
                    skipped++;
                    continue;
                }
                try {
                    if (news.publish(new PublishRequest(candidate.id(), section.slug(),
                            section.enfoque(), candidate.model(), candidate.promptVersion(),
                            reglas(section), now))) {
                        published++;
                    } else {
                        skipped++;
                    }
                } catch (RuntimeException e) {
                    notes.add("no_publicada:" + candidate.id() + ":" + e.getMessage());
                    log.warn("No se pudo publicar automaticamente {}: {}", candidate.id(), e.getMessage());
                }
            }
        }

        last = new Report(now, "ejecutada", classified, published, skipped, List.copyOf(notes));
        log.info("Autopublicacion: clasificadas={} publicadas={} descartadas={} notas={}",
                classified, published, skipped, notes);
        return last;
    }

    private int classifyPending(Instant now, List<String> catalogo, List<String> notes) {
        if (catalogo.isEmpty()) return 0;
        int classified = 0;
        for (Candidate candidate : news.forClassification(
                new ClassificationQuery(now.minus(AutoPublishRules.WINDOW), now, batchSize))) {
            try {
                ClassifyArticleUseCase.Classification result =
                        classifier.classify(candidate.title(), catalogo, FALLBACK_SECTION);
                if (news.applyClassification(new AutoPublishUseCase.Classification(
                        candidate.id(), result.section(), result.model(), result.promptVersion(),
                        result.confidence()), now)) {
                    classified++;
                }
            } catch (DomainException e) {
                // Un fallo del proveedor o del presupuesto detiene la clasificación; un
                // dato malo de una pieza se anota y se sigue con las demás.
                if (e.code() != null && e.code().startsWith("AI_")) {
                    notes.add("clasificacion_detenida:" + e.code());
                    log.warn("Clasificacion detenida ({}): {}", e.code(), e.getMessage());
                    break;
                }
                notes.add("no_clasificada:" + candidate.id() + ":" + e.code());
                log.warn("No se pudo clasificar {}: {}", candidate.id(), e.getMessage());
            }
        }
        return classified;
    }

    private static List<String> reglas(SectionView section) {
        List<String> rules = new ArrayList<>();
        rules.add("ventana_48h");
        rules.add("fuente_confianza");
        rules.add("clasificada_por_modelo");
        rules.add("seccion=" + section.slug());
        rules.add("cupo_diario=" + section.autoLimiteDiario());
        rules.add("sin_repetir_titular_7d");
        rules.add("solo_fuente_publica_adr009");
        rules.add("sin_reescribir_texto");
        return rules;
    }

    private static Instant dayStart(Instant now) {
        return now.atZone(ZONE).toLocalDate().atStartOfDay(ZONE).toInstant();
    }

    private static Instant dayEnd(Instant now) {
        return dayStart(now).plus(Duration.ofDays(1));
    }

    @Override
    public Report lastRun() {
        return last;
    }

    @Override
    public List<SectionStatus> sections() {
        Instant now = clock.instant();
        List<SectionStatus> status = new ArrayList<>();
        for (SectionView s : sections.sections()) {
            long hoy = news.publishedToday(s.slug(), dayStart(now), dayEnd(now));
            status.add(new SectionStatus(s.slug(), s.nombre(), s.activa(), s.autoPublica(),
                    s.enfoque().toUpperCase(Locale.ROOT), s.autoLimiteDiario(), hoy));
        }
        return status;
    }
}
