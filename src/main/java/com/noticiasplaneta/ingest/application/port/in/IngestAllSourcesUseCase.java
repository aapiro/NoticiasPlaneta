package com.noticiasplaneta.ingest.application.port.in;

/** Ejecuta la ingesta de todas las fuentes activas. Lo llama el programador periódico. */
public interface IngestAllSourcesUseCase {

    int runAll();
}
