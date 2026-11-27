package com.noticiasplaneta.ingest.application.port.out;

import com.noticiasplaneta.ingest.application.port.in.FuenteAdminUseCase;

import java.util.List;

/**
 * Avisos de fuente leídos de la auditoría (backlog #7).
 *
 * <p>Va sobre {@code audit_log} en vez de una tabla nueva: el aviso ya se registra al
 * degradarse la fuente, y duplicarlo en otra tabla obligaría a mantener dos verdades.
 */
public interface AvisoRepository {

    /** Últimas degradaciones y recuperaciones, de la más reciente a la más antigua. */
    List<FuenteAdminUseCase.Aviso> recientes(int limite);
}
