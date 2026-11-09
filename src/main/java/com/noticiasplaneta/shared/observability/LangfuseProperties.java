package com.noticiasplaneta.shared.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de Langfuse.
 *
 * <p>Si faltan las claves, el tracing queda desactivado y la aplicación no cambia su
 * comportamiento (no-op). Las claves se leen del entorno, nunca del repositorio.
 */
@ConfigurationProperties(prefix = "langfuse")
public record LangfuseProperties(
        boolean enabled,
        String host,
        String publicKey,
        String secretKey,
        double sampleRate,
        String serviceName,
        String environment,
        int maxPayloadChars) {

    public LangfuseProperties {
        host = (host == null || host.isBlank())
                ? "https://cloud.langfuse.com"
                : host.replaceAll("/+$", "");
        publicKey = publicKey == null ? "" : publicKey.trim();
        secretKey = secretKey == null ? "" : secretKey.trim();
        sampleRate = (sampleRate <= 0 || sampleRate > 1) ? 1.0 : sampleRate;
        serviceName = (serviceName == null || serviceName.isBlank()) ? "noticiasplaneta" : serviceName;
        environment = (environment == null || environment.isBlank()) ? "production" : environment;
        maxPayloadChars = maxPayloadChars <= 0 ? 30_000 : maxPayloadChars;
    }

    /** Solo se emiten trazas si está habilitado y hay claves. */
    public boolean active() {
        return enabled && !publicKey.isBlank() && !secretKey.isBlank();
    }
}
