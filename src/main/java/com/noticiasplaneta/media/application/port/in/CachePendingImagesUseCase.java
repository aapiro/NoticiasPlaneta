package com.noticiasplaneta.media.application.port.in;

/**
 * Cachea en lote las fotos que faltan (backlog #2).
 *
 * <p>Se lanza desde el panel: descarga, limpia y adjunta las imágenes de los
 * publicados que aún apuntan al medio de origen.
 */
public interface CachePendingImagesUseCase {

    Reporte cachear(int limite);

    /** Cuántos publicados siguen apuntando al medio de origen. */
    long pendientes();

    record Reporte(int intentadas, int cacheadas, int fallidas, long pendientesDespues) {
    }
}
