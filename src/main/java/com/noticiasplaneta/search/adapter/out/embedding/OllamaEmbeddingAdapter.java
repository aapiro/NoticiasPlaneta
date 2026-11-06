package com.noticiasplaneta.search.adapter.out.embedding;

import com.noticiasplaneta.search.application.port.out.EmbeddingPort;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * Embeddings locales con bge-m3 servido por Ollama (ADR-006).
 *
 * <p>Si Ollama no responde, la excepción sube y F10 degrada a búsqueda léxica: nunca se
 * rompe la búsqueda del lector por un fallo del modelo.
 */
@Component
class OllamaEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel model;

    OllamaEmbeddingAdapter(EmbeddingModel model) {
        this.model = model;
    }

    @Override
    public int dimensions() {
        return model.dimensions();
    }

    @Override
    public float[] embed(String text) {
        return model.embed(text);
    }
}
