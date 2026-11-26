package com.noticiasplaneta.ai.application.port.out;

import java.util.List;

/**
 * Clasificación editorial por modelo (F6).
 *
 * <p>Devuelve la sección del catálogo cerrado que mejor encaja con el titular. Solo ve
 * el titular, que ya es texto público de la fuente (ADR-009); nunca el cuerpo de un
 * borrador de producción propia.
 */
public interface ClassificationPort {

    Classification classify(String title, List<String> allowedSections, String fallbackSection);

    record Classification(String section, double confidence, String model, String promptVersion) {
    }
}
