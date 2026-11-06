package com.noticiasplaneta.search.application.port.out;

/**
 * Generador de embeddings. En fase 1 lo implementa bge-m3 local vía Ollama; en pruebas, un
 * doble determinista. El dominio de búsqueda no sabe qué modelo hay detrás.
 */
public interface EmbeddingPort {

    int dimensions();

    float[] embed(String text);
}
