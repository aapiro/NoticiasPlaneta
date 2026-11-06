package com.noticiasplaneta.ingest.application.port.out;

/** Evita dos ejecuciones simultáneas sobre el mismo recurso (E05 de F01). */
public interface ExecutionLock {

    /** @return true si se ha obtenido el bloqueo. */
    boolean tryAcquire(String key);

    void release(String key);
}
