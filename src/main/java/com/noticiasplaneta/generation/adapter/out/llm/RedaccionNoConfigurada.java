package com.noticiasplaneta.generation.adapter.out.llm;

import com.noticiasplaneta.generation.application.port.out.RedaccionPort;
import com.noticiasplaneta.shared.domain.DomainException;

/**
 * Sin proveedor de IA no hay borrador, y se dice con un código inequívoco.
 *
 * <p>Devolver un borrador vacío «para que no falle» sería peor: el editor creería que
 * el tema no tiene cobertura cuando el problema es que falta la clave.
 */
public class RedaccionNoConfigurada implements RedaccionPort {

    @Override
    public Resultado redactar(Peticion peticion) {
        throw new DomainException("AI_NOT_CONFIGURED",
                "No hay proveedor de IA configurado (falta ai.deepseek.api-key)");
    }
}
