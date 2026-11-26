package com.noticiasplaneta.ingest.application.port.in;

import java.util.List;

/**
 * Fuentes de confianza para la publicación automática (F6).
 *
 * <p>Marcar una fuente aquí significa "sus piezas pueden publicarse solas si la sección
 * está en automático". Apagado por defecto: sin marcar nada, la automatización no
 * publica, que es el fallo correcto.
 */
public interface SourceTrustUseCase {

    List<SourceTrustView> sources();

    /** @return {@code false} si la fuente no existe */
    boolean setTrusted(String sourceId, boolean trusted, String updatedBy);

    record SourceTrustView(String id, String name, String state, boolean trusted, int failureCount) {
    }
}
