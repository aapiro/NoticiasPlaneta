package com.noticiasplaneta.ai.application.port.in;

import java.util.List;

/**
 * Gestión de las capacidades de IA desde el panel (ADR-011, F1).
 *
 * <p>El adaptador de entrada (el panel) solo habla por este puerto: no toca el dominio
 * ni la persistencia del módulo {@code ai}. De ahí que los tipos de aquí sean propios.
 */
public interface AiCapabilitiesAdminUseCase {

    /** Estado de todas las capacidades, en el orden del catálogo. */
    List<CapabilityView> capabilities();

    /** Modos disponibles para el desplegable. */
    List<String> modes();

    /**
     * Cambia el modo de una capacidad y lo deja persistido.
     *
     * @return {@code false} si la capacidad o el modo no existen
     */
    boolean setMode(String capability, String mode, String updatedBy);

    record CapabilityView(String capability, String mode, boolean on) {
    }
}
