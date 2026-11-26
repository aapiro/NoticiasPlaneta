package com.noticiasplaneta.news.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * F6 — Publicación automática de piezas de fuente externa.
 *
 * <p>Este puerto es el único camino por el que el módulo de automatización puede tocar
 * artículos: selecciona candidatos, aplica la clasificación del modelo y publica con la
 * marca de automática. No expone el agregado ni la persistencia.
 *
 * <p>Las reglas duras (ventana de 48 h, fuente de confianza, cupo diario, sin repetir
 * pieza, solo piezas de fuente pública) son las que aplican los métodos de aquí.
 */
public interface AutoPublishUseCase {

    /**
     * Borradores pendientes de clasificar: fuente de confianza, dentro de la ventana,
     * sección activa y sin clasificación previa.
     */
    List<Candidate> forClassification(ClassificationQuery query);

    /**
     * Borradores listos para publicar en una sección: ya clasificados (o ya en la
     * sección), dentro de la ventana y sin repetir un titular ya publicado.
     */
    List<Candidate> forPublication(PublicationQuery query);

    /**
     * Mueve el borrador a la sección que dijo el modelo y guarda la traza de la
     * clasificación. Si la pieza ya no está en DRAFT no se toca.
     *
     * @return {@code false} si el artículo no existe o ya no es un borrador
     */
    boolean applyClassification(Classification classification, Instant now);

    /**
     * Publica una pieza aplicando ADR-009: solo origen {@code INGEST} (texto público de
     * la fuente) y sin reescribir titular ni cuerpo.
     *
     * @return {@code false} si la pieza ya no era publicable (por ejemplo, otra pasada la publicó)
     */
    boolean publish(PublishRequest request);

    /** Piezas publicadas automáticamente en una sección dentro del intervalo (cupo diario). */
    long publishedToday(String sectionId, Instant from, Instant to);

    record ClassificationQuery(Instant from, Instant to, int limit) {
    }

    record PublicationQuery(String sectionId, Instant from, Instant to, Instant publishedSince, int limit) {
    }

    record Candidate(String id, String title, String sectionId, String sourceName, Instant sourceDate,
                     String model, String promptVersion) {
    }

    record Classification(String articleId, String sectionId, String model, String promptVersion,
                          double confidence) {
    }

    record PublishRequest(String articleId, String sectionId, String bias, String model,
                          String promptVersion, List<String> rules, Instant now) {
    }
}
