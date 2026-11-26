package com.noticiasplaneta.generation.domain;

import java.util.Locale;

/**
 * Traduce el enfoque declarado de la sección (ADR-012) a una instrucción para el modelo.
 *
 * <p>El catálogo es cerrado, así que el sesgo es comparable entre secciones. La
 * instrucción decide el ángulo; nunca autoriza a inventar ni a omitir un dato de la
 * fuente, y el prompt lo dice de forma explícita.
 */
public final class InstruccionEnfoque {

    private InstruccionEnfoque() {
    }

    public static String para(String enfoque) {
        return switch (enfoque == null ? "" : enfoque.trim().toUpperCase(Locale.ROOT)) {
            case "IZQUIERDA" -> "Línea editorial de izquierda, declarada al lector: explica el hecho "
                    + "desde su impacto en derechos sociales, condiciones laborales y servicios "
                    + "públicos, y nombra a los afectados cuando el material lo permita.";
            case "DERECHA" -> "Línea editorial de derecha, declarada al lector: explica el hecho "
                    + "desde la libertad económica, la responsabilidad fiscal, la seguridad y la "
                    + "iniciativa privada, con los datos que dé el material.";
            case "CENTRO" -> "Línea editorial de centro, declarada al lector: contrasta las "
                    + "posiciones que aparezcan en el material y atribuye cada dato a quien lo "
                    + "sostiene, sin tomar partido.";
            default -> "Sin ángulo editorial declarado: informa del hecho y de sus datos, sin "
                    + "interpretar ni valorar.";
        };
    }

    /** Sufijo que se añade a cualquier instrucción: los hechos no se tocan. */
    public static String limite() {
        return "Aplica el enfoque al ángulo y al titular, pero no inventes, no omitas y no "
                + "suavices ningún dato que aparezca en el material, aunque contradiga ese ángulo.";
    }
}
