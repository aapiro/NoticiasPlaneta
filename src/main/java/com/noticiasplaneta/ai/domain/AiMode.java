package com.noticiasplaneta.ai.domain;

/**
 * Modo de ejecución de una capacidad (ADR-011).
 *
 * <p>{@code HYBRID} es el recomendado: embeddings locales (coste cero, nada sale
 * del servidor) y generación externa solo cuando aporta calidad.
 */
public enum AiMode {
    /** No se ejecuta: la interfaz no ofrece la capacidad. */
    OFF,
    /** Ollama en el propio servidor: coste cero, más lento, calidad media. */
    LOCAL,
    /** Proveedor externo (DeepSeek): céntimos, rápido, calidad alta. */
    EXTERNAL,
    /** Embeddings locales + generación externa. */
    HYBRID;

    public boolean isOn() {
        return this != OFF;
    }

    /** Un modo desconocido nunca enciende nada. */
    public static AiMode parse(String raw) {
        if (raw == null || raw.isBlank()) return OFF;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OFF;
        }
    }
}
