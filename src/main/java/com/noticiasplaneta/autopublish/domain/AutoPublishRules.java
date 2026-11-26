package com.noticiasplaneta.autopublish.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * F6 — Reglas duras de la publicación automática.
 *
 * <p>Viven aquí, sin framework, porque son el motivo por el que esta función se puede
 * encender sin que nadie revise: si una regla se relaja, se relaja a la vista y con un
 * test que falla. Las consultas SQL del adaptador aplican las mismas reglas; estas
 * funciones son la definición canónica y se usan además como segunda barrera.
 */
public final class AutoPublishRules {

    /** Ventana de actualidad: una pieza más vieja que esto no se publica sola. */
    public static final Duration WINDOW = Duration.ofHours(48);

    /** Anti-repetición: no se publica un titular ya publicado en este periodo. */
    public static final Duration REPEAT_WINDOW = Duration.ofDays(7);

    /** Una fuente que lleva demasiados fallos no publica sola aunque esté marcada. */
    public static final String DISABLED_STATE = "DISABLED";

    private AutoPublishRules() {
    }

    /**
     * ¿La fecha de la noticia cae dentro de la ventana?
     *
     * <p>Se mide sobre la fecha de la noticia ({@code coalesce(published_at, created_at)}),
     * no sobre cuándo se insertó la fila: si no, todas empatan.
     */
    public static boolean withinWindow(Instant sourceDate, Instant now) {
        if (sourceDate == null || now == null) return false;
        return !sourceDate.isAfter(now) && sourceDate.isAfter(now.minus(WINDOW));
    }

    /** Fuente de confianza = marcada por un editor y no deshabilitada. */
    public static boolean trustedSource(boolean confianza, String state) {
        return confianza && state != null && !DISABLED_STATE.equalsIgnoreCase(state);
    }

    /** ¿Queda cupo hoy? Un límite 0 significa "esta sección no publica sola". */
    public static boolean quotaAvailable(long publishedToday, int dailyLimit) {
        return dailyLimit > 0 && publishedToday < dailyLimit;
    }

    /** Normaliza un titular para comparar repeticiones (misma lógica que el SQL). */
    public static String normalizeTitle(String title) {
        if (title == null) return "";
        return title.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9áéíóúüñ]+", " ")
                .trim();
    }
}
