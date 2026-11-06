package com.noticiasplaneta.identity.adapter.in.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de seguridad.
 *
 * @param apiKey      clave que presentan los agentes en la cabecera X-Api-Key
 * @param agentId     identidad que se atribuye a esa clave (va a auditoría)
 * @param rpId        relying party de WebAuthn (el dominio)
 * @param rpName      nombre visible del relying party
 * @param allowedOrigins orígenes permitidos para WebAuthn
 */
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
        String apiKey,
        String agentId,
        String rpId,
        String rpName,
        java.util.List<String> allowedOrigins) {

    public SecurityProperties {
        agentId = agentId == null || agentId.isBlank() ? "agent" : agentId;
        rpId = rpId == null ? "localhost" : rpId;
        rpName = rpName == null ? "Noticias Planeta" : rpName;
        allowedOrigins = allowedOrigins == null ? java.util.List.of("https://localhost") : allowedOrigins;
    }

    public boolean apiKeyEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
