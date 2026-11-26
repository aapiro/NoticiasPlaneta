package com.noticiasplaneta.edicion.application.port.in;

import java.util.List;

/**
 * Todo lo que necesita {@code /admin/edicion} en una sola lectura.
 *
 * @param ultima          la última ejecución, o nulo si nunca se ha lanzado ninguna
 * @param secciones       estado por sección, en el orden del portal
 * @param historico       ejecuciones anteriores (la última incluida), de más nueva a más vieja
 * @param ejecucionEnCurso cierto si ahora mismo hay una ejecución abierta
 * @param totalPublicadas piezas publicadas en el portal (contexto de la portada)
 */
public record TableroEdicionView(
        EjecucionView ultima,
        List<SeccionEstadoView> secciones,
        List<EjecucionView> historico,
        boolean ejecucionEnCurso,
        long totalPublicadas) {
}
