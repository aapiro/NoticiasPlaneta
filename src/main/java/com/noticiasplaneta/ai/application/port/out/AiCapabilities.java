package com.noticiasplaneta.ai.application.port.out;

import com.noticiasplaneta.ai.domain.AiCapability;
import com.noticiasplaneta.ai.domain.AiMode;

/**
 * Puerta única de las capacidades de IA (ADR-011).
 *
 * <p>Todo lo que pregunte "¿esto está encendido?" pasa por aquí, para que haya
 * una sola fuente de verdad y se pueda cambiar a BD, a configuración o a un
 * servicio de entitlements sin tocar los casos de uso.
 */
public interface AiCapabilities {

    AiMode modeOf(AiCapability capability);

    default boolean isOn(AiCapability capability) {
        return modeOf(capability).isOn();
    }
}
