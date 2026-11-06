package com.noticiasplaneta.ai.application.port.in;

import java.util.UUID;

public interface EnrichArticleUseCase {

    /** Encola el enriquecido de un artículo (idempotente por artículo y tipo). */
    void enqueue(UUID articleId);

    /** Procesa hasta {@code max} trabajos pendientes. Devuelve cuántos se han completado. */
    int processPending(int max);
}
