package com.noticiasplaneta.ai.application.service;

import com.noticiasplaneta.ai.application.port.in.CapacidadesIaUseCase;
import com.noticiasplaneta.ai.application.port.out.AiCapabilities;
import com.noticiasplaneta.ai.domain.AiCapability;

import java.util.Locale;

/** Lectura de capacidades por nombre, con «desconocida = apagada». */
public class CapacidadesIaService implements CapacidadesIaUseCase {

    private final AiCapabilities capabilities;

    public CapacidadesIaService(AiCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public boolean activa(String capacidad) {
        if (capacidad == null || capacidad.isBlank()) {
            return false;
        }
        try {
            return capabilities.isOn(AiCapability.valueOf(capacidad.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
