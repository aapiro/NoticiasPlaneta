package com.noticiasplaneta.ai.adapter.out.llm;

import com.noticiasplaneta.ai.application.port.out.AiCapabilities;
import com.noticiasplaneta.ai.application.port.out.EnrichmentPort;
import com.noticiasplaneta.ai.domain.AiCapability;
import com.noticiasplaneta.shared.domain.DomainException;

/**
 * Puerta sobre el puerto de enriquecido (ADR-011).
 *
 * <p>Si la capacidad está apagada no se llama al proveedor: ni coste, ni datos que
 * salgan del servidor. Se lanza {@code AI_CAPABILITY_DISABLED} para que el job quede
 * con un código claro en vez de con un resultado vacío que parezca bueno.
 */
public class GatedEnrichmentPort implements EnrichmentPort {

    static final String CODE = "AI_CAPABILITY_DISABLED";

    private final AiCapabilities capabilities;
    private final EnrichmentPort delegate;

    public GatedEnrichmentPort(AiCapabilities capabilities, EnrichmentPort delegate) {
        this.capabilities = capabilities;
        this.delegate = delegate;
    }

    @Override
    public EnrichmentResult enrich(String title, String body) {
        if (!capabilities.isOn(AiCapability.ENRICH_TAGS)) {
            throw new DomainException(CODE, "La capacidad de enriquecido está apagada (ADR-011)");
        }
        return delegate.enrich(title, body);
    }
}
