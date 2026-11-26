package com.noticiasplaneta.edicion.application.port.in;

/**
 * Informe de una ejecución de la edición, visto por el panel.
 *
 * @param id            identificador de la ejecución (enlace al detalle)
 * @param iniciada      ISO-8601 del arranque
 * @param terminada     ISO-8601 del cierre, o nulo si sigue en curso
 * @param duracionTexto duración legible («14,5 s»), vacía si sigue en curso
 * @param actor         quién la lanzó
 * @param modo          «publicar» o «borrador»
 * @param estado        EN_CURSO, COMPLETADA, PARCIAL o FALLIDA
 * @param fuentes       fuentes consultadas
 * @param fuentesConError fuentes que respondieron con error
 * @param creadas       items nuevos que trajo la ingesta
 * @param borradores    borradores creados desde esos items
 * @param publicadas    piezas publicadas por esta ejecución
 * @param errores       fallos acumulados (fuentes + publicaciones)
 * @param detalle       explicación de los fallos, o nulo
 */
public record EjecucionView(
        String id,
        String iniciada,
        String terminada,
        String duracionTexto,
        String actor,
        String modo,
        String estado,
        int fuentes,
        int fuentesConError,
        int creadas,
        int borradores,
        int publicadas,
        int errores,
        String detalle) {

    public boolean exitosa() {
        return "COMPLETADA".equals(estado);
    }

    /** ¿Merece la pena sacar el detalle de errores en la tabla? */
    public boolean conIncidencias() {
        return errores > 0 || "PARCIAL".equals(estado) || "FALLIDA".equals(estado);
    }
}
