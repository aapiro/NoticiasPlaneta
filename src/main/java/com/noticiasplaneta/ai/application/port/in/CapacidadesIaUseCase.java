package com.noticiasplaneta.ai.application.port.in;

/**
 * Capacidades de IA encendidas, por nombre (ADR-011), para otros módulos.
 *
 * <p>Se pregunta por el nombre de la capacidad, no por el enum del dominio de IA: así
 * el módulo que llama no se acopla a los internals de {@code ai}. Una capacidad
 * desconocida se considera apagada: ante la duda, no se gasta ni se publica.
 */
public interface CapacidadesIaUseCase {

    boolean activa(String capacidad);
}
