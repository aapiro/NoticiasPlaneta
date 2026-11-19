package com.noticiasplaneta.ai.adapter.out.config;

import com.noticiasplaneta.ai.application.port.out.AiCapabilities;
import com.noticiasplaneta.ai.domain.AiCapability;
import com.noticiasplaneta.ai.domain.AiMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementación inicial: lee los modos de {@code application.yml} (ADR-011, fase F0).
 *
 * <p>En F1 se sustituye por la que lee de la tabla {@code feature_flag}, que manda
 * sobre esta. Mientras tanto, sin configuración, todo queda en {@link AiMode#OFF}.
 */
@Component
@ConfigurationProperties(prefix = "features.ai")
public class ConfigAiCapabilities implements AiCapabilities {

    /** Clave = nombre de la capacidad (ENRICH_TAGS...), valor = modo. */
    private Map<String, String> capabilities = new HashMap<>();

    public void setCapabilities(Map<String, String> capabilities) {
        this.capabilities = capabilities == null ? new HashMap<>() : capabilities;
    }

    public Map<String, String> getCapabilities() {
        return capabilities;
    }

    @Override
    public AiMode modeOf(AiCapability capability) {
        if (capability == null) return AiMode.OFF;
        return AiMode.parse(capabilities.get(capability.name()));
    }
}
