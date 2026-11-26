package com.noticiasplaneta.edicion.domain;

/** Estado de una ejecución de la edición. */
public enum EdicionEstado {

    /** En marcha: bloquea cualquier otra ejecución (índice único parcial en la BD). */
    EN_CURSO,

    /** Terminó sin ningún error. Puede haber publicado cero piezas y ser correcto. */
    COMPLETADA,

    /** Terminó, pero alguna fuente o alguna publicación falló. */
    PARCIAL,

    /** No se pudo sacar nada adelante. */
    FALLIDA;

    public static EdicionEstado parse(String raw) {
        if (raw == null || raw.isBlank()) return FALLIDA;
        try {
            return valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return FALLIDA;
        }
    }
}
