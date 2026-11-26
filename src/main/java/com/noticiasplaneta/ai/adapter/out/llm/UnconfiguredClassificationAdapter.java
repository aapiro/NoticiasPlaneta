package com.noticiasplaneta.ai.adapter.out.llm;

import com.noticiasplaneta.ai.application.port.out.ClassificationPort;
import com.noticiasplaneta.shared.domain.DomainException;

import java.util.List;

/**
 * Sin proveedor configurado no se clasifica: se falla con un código claro.
 *
 * <p>Devolver la sección de reserva aquí sería peor que fallar: la automatización
 * publicaría con una clasificación que nadie ha hecho.
 */
public class UnconfiguredClassificationAdapter implements ClassificationPort {

    @Override
    public Classification classify(String title, List<String> allowedSections, String fallbackSection) {
        throw new DomainException("AI_NOT_CONFIGURED",
                "No hay proveedor de IA configurado: la clasificacion automatica esta apagada");
    }
}
