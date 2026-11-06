package com.noticiasplaneta.shared.ai;

/**
 * Generador de embeddings. Vive en el kernel porque lo usan dos módulos: búsqueda (F10)
 * y enriquecido (F03). El dominio no sabe qué modelo hay detrás.
 */
public interface EmbeddingPort {

    int dimensions();

    float[] embed(String text);
}
