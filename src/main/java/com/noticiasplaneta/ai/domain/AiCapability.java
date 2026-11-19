package com.noticiasplaneta.ai.domain;

/**
 * Capacidades de IA que el producto puede encender por separado (ADR-011).
 *
 * <p>Se activan capacidades, no proveedores: el proveedor es una decisión de
 * infraestructura por capacidad, no algo que el usuario elija.
 */
public enum AiCapability {
    /** Búsqueda por significado (embeddings). */
    SEARCH_SEMANTIC,
    /** Etiquetas en español. */
    ENRICH_TAGS,
    /** Personas, organizaciones y lugares. */
    ENRICH_ENTITIES,
    /** Resumen de dos frases. */
    ENRICH_SUMMARY,
    /** Noticias relacionadas. */
    ARTICLE_RELATED,
    /** Publicar automáticamente tras enriquecer. Requiere reglas y límite diario. */
    PUBLISH_AUTO
}
