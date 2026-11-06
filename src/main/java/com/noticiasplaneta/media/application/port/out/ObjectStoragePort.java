package com.noticiasplaneta.media.application.port.out;

import java.time.Duration;

/**
 * Almacenamiento de objetos. En fase 1 lo implementa el disco local; en fase 2, S3/R2.
 * Cambiar de uno a otro no toca dominio ni casos de uso (ADR-008).
 */
public interface ObjectStoragePort {

    /** Devuelve una URL de subida directa, válida durante {@code ttl}. */
    String presignPut(String key, String contentType, Duration ttl);

    void put(String key, byte[] content);

    byte[] get(String key);

    /** Valida la firma y escribe el objeto. Lanza {@code UPLOAD_TOKEN_INVALID} si no cuadra. */
    void putSigned(String key, long expiresAt, String token, byte[] content);

    /** Borra el objeto si existe. No falla si no está. */
    void delete(String key);
}
