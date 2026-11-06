package com.noticiasplaneta.ai.application.port.out;

import java.util.List;

/**
 * Extrae metadatos de un artículo. No reescribe el texto editorial (RN1 de F03).
 */
public interface EnrichmentPort {

    EnrichmentResult enrich(String title, String body);

    record Entity(String name, String type) {
    }

    record EnrichmentResult(
            List<String> tags,
            List<Entity> entities,
            String summary,
            String model,
            String promptVersion,
            String provider,
            long costMicros) {
    }
}
