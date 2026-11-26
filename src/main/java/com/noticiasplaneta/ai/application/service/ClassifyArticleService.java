package com.noticiasplaneta.ai.application.service;

import com.noticiasplaneta.ai.application.port.in.ClassifyArticleUseCase;
import com.noticiasplaneta.ai.application.port.out.BudgetPort;
import com.noticiasplaneta.ai.application.port.out.ClassificationPort;
import com.noticiasplaneta.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/**
 * F6 — clasificación con red de seguridad.
 *
 * <p>Una clasificación que no se puede pagar, que falla o que devuelve una sección fuera
 * del catálogo no se convierte en una sección adivinada: se devuelve la sección de
 * reserva y la pieza no se publica sola. Fallar hacia "no publicar" es la decisión de
 * diseño; lo contrario publicaría basura con marca de automática.
 */
public class ClassifyArticleService implements ClassifyArticleUseCase {

    private static final Logger log = LoggerFactory.getLogger(ClassifyArticleService.class);

    /**
     * Coste estimado de clasificar un titular (ADR-017). Medido contra DeepSeek con el
     * prompt real: 184 tokens de entrada y 17 de salida = 75,6 microdólares en hora punta
     * y sin caché; 150 deja margen para titulares y catálogos más largos.
     */
    static final long ESTIMATED_COST_MICROS = 150;

    /** Por debajo de esto el modelo no se cree su propia respuesta. */
    static final double MIN_CONFIDENCE = 0.55;

    private final ClassificationPort classifier;
    private final BudgetPort budget;

    public ClassifyArticleService(ClassificationPort classifier, BudgetPort budget) {
        this.classifier = classifier;
        this.budget = budget;
    }

    @Override
    public Classification classify(String title, List<String> allowedSections, String fallbackSection) {
        if (title == null || title.isBlank() || allowedSections == null || allowedSections.isEmpty()) {
            throw new DomainException("CLASSIFY_INPUT_INVALID", "Sin titular o sin catálogo no hay clasificación");
        }
        if (!budget.tryConsume(ESTIMATED_COST_MICROS)) {
            throw new DomainException("AI_BUDGET_EXHAUSTED", "Presupuesto de IA agotado para clasificar");
        }

        ClassificationPort.Classification result = classifier.classify(title, allowedSections, fallbackSection);

        String section = result.section() == null ? null : result.section().trim().toLowerCase(Locale.ROOT);
        boolean allowed = section != null && allowedSections.stream().anyMatch(s -> s.equalsIgnoreCase(section));
        if (!allowed) {
            log.info("Clasificación fuera del catálogo ('{}'): se usa la sección de reserva {}", section, fallbackSection);
            return new Classification(fallbackSection, 0, result.model(), result.promptVersion());
        }
        if (result.confidence() < MIN_CONFIDENCE) {
            log.info("Clasificación poco fiable ({} < {}): se usa la sección de reserva {}",
                    result.confidence(), MIN_CONFIDENCE, fallbackSection);
            return new Classification(fallbackSection, result.confidence(), result.model(), result.promptVersion());
        }
        return new Classification(section, result.confidence(), result.model(), result.promptVersion());
    }
}
