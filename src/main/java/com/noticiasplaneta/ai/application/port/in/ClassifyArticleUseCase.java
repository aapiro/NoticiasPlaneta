package com.noticiasplaneta.ai.application.port.in;

import java.util.List;

/**
 * Clasificación real de una pieza en una sección del catálogo (F6).
 *
 * <p>Sustituye al {@code ilike} del script de edición, que mandaba casi todo a
 * {@code general}. La sección devuelta pertenece siempre a la lista recibida: si el
 * modelo contesta algo fuera del catálogo, se cae al titular de reserva, nunca se
 * inventa una sección.
 */
public interface ClassifyArticleUseCase {

    Classification classify(String title, List<String> allowedSections, String fallbackSection);

    record Classification(String section, double confidence, String model, String promptVersion) {
    }
}
