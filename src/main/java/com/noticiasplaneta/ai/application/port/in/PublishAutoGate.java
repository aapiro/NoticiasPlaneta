package com.noticiasplaneta.ai.application.port.in;

/**
 * Interruptor global de la publicación automática (ADR-011, capacidad {@code PUBLISH_AUTO}).
 *
 * <p>Es el botón de emergencia del sistema entero: con esto apagado no se clasifica ni
 * se publica nada solo, aunque todas las secciones estén marcadas. La fuente de verdad
 * es la tabla de capacidades, así que apagarlo surte efecto en la siguiente pasada sin
 * reiniciar.
 */
public interface PublishAutoGate {

    boolean enabled();
}
