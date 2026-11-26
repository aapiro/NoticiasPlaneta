package com.noticiasplaneta.edicion.application.port.in;

import java.util.List;

/** Una ejecución con sus piezas: la «edición generada» que enseña el panel. */
public record EjecucionDetalleView(EjecucionView ejecucion, List<PiezaEjecucionView> piezas) {
}
