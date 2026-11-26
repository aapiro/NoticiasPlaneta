package com.noticiasplaneta.ingest.application.port.in;

import java.util.List;

/** Ejecuta la ingesta de todas las fuentes activas. Lo llama el programador periódico. */
public interface IngestAllSourcesUseCase {

    int runAll();

    /**
     * Igual que {@link #runAll()}, pero con el detalle por fuente que necesita el informe de
     * la edición: cuántas piezas trajo cada una y qué falló.
     *
     * <p>Consulta las fuentes {@code ACTIVE} y también {@code DEGRADED}: degradada es un
     * aviso, no una baja, y filtrar solo por {@code ACTIVE} deja de ingerir en cuanto todas
     * acumulan fallos, sin decir por qué.
     */
    InformeFuentes runDetailed();

    record InformeFuentes(List<FuenteIngesta> fuentes, int creadas, int errores) {
    }

    /** @param error nulo si la fuente respondió bien */
    record FuenteIngesta(String id, String nombre, int creadas, String error) {
    }
}
