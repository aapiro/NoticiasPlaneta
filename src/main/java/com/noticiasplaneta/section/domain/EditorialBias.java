package com.noticiasplaneta.section.domain;

/**
 * Línea editorial declarada de una sección (ADR-012).
 *
 * <p>Catálogo cerrado a propósito: así el sesgo es comparable entre secciones y
 * queda auditable. El enfoque decide qué se elige y cómo se explica; nunca
 * autoriza a inventar ni a omitir lo que dice la fuente.
 */
public enum EditorialBias {
    IZQUIERDA,
    DERECHA,
    CENTRO,
    /** Sin línea editorial: se informa sin ángulo declarado. */
    NEUTRAL;

    public static EditorialBias parse(String raw) {
        if (raw == null || raw.isBlank()) return NEUTRAL;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NEUTRAL;
        }
    }
}
