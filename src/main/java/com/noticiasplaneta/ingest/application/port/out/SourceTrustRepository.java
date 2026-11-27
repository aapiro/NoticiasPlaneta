package com.noticiasplaneta.ingest.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vista y edición de la marca de confianza de las fuentes (F6).
 *
 * <p>Separado de {@code SourceRepository} porque la confianza no es identidad de la
 * fuente: es una decisión operativa sobre si sus piezas se publican solas. Va por SQL
 * directo para no reescribir el agregado de ingesta por un booleano.
 */
public interface SourceTrustRepository {

    List<Row> findAll();

    boolean setTrusted(UUID sourceId, boolean trusted);

    record Row(String id, String name, String feedUrl, String state, boolean trusted,
               int failureCount, Instant lastCheckedAt) {
    }
}
