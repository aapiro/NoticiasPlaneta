package com.noticiasplaneta.taxonomy.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Línea temporal de un tema.
 *
 * @param deltaSince  instante desde el que se calcula el bloque "desde tu última visita"
 *                    (null si es la primera visita)
 * @param delta       artículos publicados después de {@code deltaSince}
 */
public record TopicTimeline(String slug, String title, String summary,
                            List<Entry> entries, Instant deltaSince, List<Entry> delta) {

    public record Entry(String articleId, String slug, String sectionId, String title,
                        Instant publishedAt, String publishedLabel) {
    }
}
