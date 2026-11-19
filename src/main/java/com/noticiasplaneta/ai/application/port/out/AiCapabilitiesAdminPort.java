package com.noticiasplaneta.ai.application.port.out;

import com.noticiasplaneta.ai.domain.AiCapability;
import com.noticiasplaneta.ai.domain.AiMode;

import java.util.Map;

/**
 * Escritura de las capacidades de IA (ADR-011, F1).
 *
 * <p>Separado de {@link AiCapabilities} (lectura) para que la capa de aplicación pueda
 * cambiar un modo sin conocer la persistencia. Lo implementa el adaptador de base de
 * datos; quien solo necesita consultar usa {@link AiCapabilities}.
 */
public interface AiCapabilitiesAdminPort {

    /** Estado de todas las capacidades. */
    Map<AiCapability, AiMode> snapshot();

    /** Persiste el modo y refresca la caché. */
    void setMode(AiCapability capability, AiMode mode, String updatedBy);
}
