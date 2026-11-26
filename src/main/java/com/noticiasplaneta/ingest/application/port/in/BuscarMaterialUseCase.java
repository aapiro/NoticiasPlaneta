package com.noticiasplaneta.ingest.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Búsqueda de material ya ingerido (F8).
 *
 * <p>Devuelve fragmentos con medio, URL y fecha: es lo que el generador puede usar, y
 * nada más. El puerto es de entrada porque el corpus es del módulo de ingesta; quien
 * busca no toca la tabla {@code ingest_item}.
 */
public interface BuscarMaterialUseCase {

    List<Material> buscar(String consulta, int limite);

    /**
     * @param itemId  identificador del item ingerido
     * @param medio   nombre de la fuente tal y como está configurada
     * @param url     URL canónica del item
     * @param fecha   publicación según la fuente, o fecha de ingesta si no la trae
     * @param titular titular original
     * @param texto   cuerpo recortado (2.000 caracteres) para el prompt
     */
    record Material(String itemId, String medio, String url, Instant fecha,
                    String titular, String texto) {
    }
}
