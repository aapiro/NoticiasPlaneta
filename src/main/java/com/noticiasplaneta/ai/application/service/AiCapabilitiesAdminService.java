package com.noticiasplaneta.ai.application.service;

import com.noticiasplaneta.ai.application.port.in.AiCapabilitiesAdminUseCase;
import com.noticiasplaneta.ai.application.port.out.AiCapabilitiesAdminPort;
import com.noticiasplaneta.ai.domain.AiCapability;
import com.noticiasplaneta.ai.domain.AiMode;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Traduce el catálogo de capacidades a tipos de puerto (ADR-011, F1).
 *
 * <p>Habla con la persistencia por {@link AiCapabilitiesAdminPort}: la capa de
 * aplicación no conoce adaptadores.
 */
@Service
class AiCapabilitiesAdminService implements AiCapabilitiesAdminUseCase {

    private final AiCapabilitiesAdminPort store;

    AiCapabilitiesAdminService(AiCapabilitiesAdminPort store) {
        this.store = store;
    }

    @Override
    public List<CapabilityView> capabilities() {
        return store.snapshot().entrySet().stream()
                .map(entry -> new CapabilityView(
                        entry.getKey().name(),
                        entry.getValue().name(),
                        entry.getValue().isOn()))
                .toList();
    }

    @Override
    public List<String> modes() {
        return Arrays.stream(AiMode.values()).map(Enum::name).toList();
    }

    @Override
    public boolean setMode(String capability, String mode, String updatedBy) {
        AiCapability target;
        try {
            target = AiCapability.valueOf(capability);
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }

        AiMode parsed = AiMode.parse(mode);
        // "off" en minúsculas es válido; basura no lo es
        if (mode == null || !parsed.name().equalsIgnoreCase(mode.trim())) return false;

        store.setMode(target, parsed, updatedBy);
        return true;
    }
}
