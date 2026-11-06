package com.noticiasplaneta.ai.adapter.out.llm;

import com.noticiasplaneta.ai.application.port.out.EnrichmentPort;
import com.noticiasplaneta.shared.domain.DomainException;

import java.util.List;

/**
 * Sin proveedor configurado no se inventa nada: el job falla con un código claro y se
 * reintenta cuando haya clave. Mejor eso que un resultado vacío que parezca bueno.
 */
public class UnconfiguredEnrichmentAdapter implements EnrichmentPort {

    @Override
    public EnrichmentResult enrich(String title, String body) {
        throw new DomainException("AI_NOT_CONFIGURED",
                "No hay proveedor de IA configurado (spring.ai.deepseek.api-key)");
    }
}
