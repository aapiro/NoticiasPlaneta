package com.noticiasplaneta.news.application.port.out;

import com.noticiasplaneta.news.application.port.in.AutoPublishUseCase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistencia de la publicación automática (F6).
 *
 * <p>Las consultas aplican las reglas duras en SQL —ventana, fuente de confianza, cupo,
 * anti-repetición— porque son condiciones de selección, no de presentación. La definición
 * canónica de cada regla sigue en {@code AutoPublishRules}.
 */
public interface AutoPublishStore {

    List<AutoPublishUseCase.Candidate> candidatesForClassification(Instant from, Instant to, int limit);

    List<AutoPublishUseCase.Candidate> candidatesForPublication(String sectionId, Instant from, Instant to,
                                                               Instant publishedSince, int limit);

    void saveClassification(UUID articleId, String sectionId, String model, String promptVersion,
                            double confidence, Instant at);

    /**
     * Inserta la marca de automática y el enfoque aplicado.
     *
     * @return {@code false} si la pieza ya tenía marca (otra pasada se adelantó)
     */
    boolean markAutoPublished(UUID articleId, String sectionId, String bias, String model,
                              String promptVersion, List<String> rules, Instant at);

    long countAutoPublished(String sectionId, Instant from, Instant to);
}
