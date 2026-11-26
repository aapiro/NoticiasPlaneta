package com.noticiasplaneta.ingest.application.port.out;

import java.time.Instant;
import java.util.List;

/** Consulta de material ingerido por texto (F8). Detrás puede haber FTS, trigramas o un motor externo. */
public interface MaterialQueryRepository {

    List<Fila> buscar(String consulta, List<String> terminos, int limite, int ventanaDias);

    record Fila(String itemId, String medio, String url, Instant fecha,
                String titular, String texto) {
    }
}
