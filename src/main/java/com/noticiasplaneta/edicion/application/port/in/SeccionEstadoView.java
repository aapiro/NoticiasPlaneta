package com.noticiasplaneta.edicion.application.port.in;

/**
 * Estado editorial de una sección tal y como lo pinta el panel.
 *
 * @param piezasHoy    piezas publicadas hoy (hora de Madrid) en esa sección
 * @param cupoRestante cupo de la edición menos lo ya publicado hoy; nunca negativo
 */
public record SeccionEstadoView(
        String slug,
        String nombre,
        boolean activa,
        boolean automatica,
        String enfoque,
        int cupo,
        int piezasHoy,
        int cupoRestante) {

    public String modoTexto() {
        return automatica ? "automática" : "manual";
    }
}
