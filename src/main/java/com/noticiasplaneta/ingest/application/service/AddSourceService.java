package com.noticiasplaneta.ingest.application.service;

import com.noticiasplaneta.ingest.application.port.in.AddSourceUseCase;
import com.noticiasplaneta.ingest.application.port.out.SourceRepository;
import com.noticiasplaneta.ingest.domain.Source;

/**
 * Registra una fuente.
 *
 * <p>Idempotente por {@code feedUrl}: registrar dos veces el mismo feed hacía que cada
 * noticia entrara multiplicada y generaba borradores duplicados en la bandeja.
 */
public class AddSourceService implements AddSourceUseCase {

    private final SourceRepository sources;

    public AddSourceService(SourceRepository sources) {
        this.sources = sources;
    }

    @Override
    public Source add(String name, String feedUrl) {
        String normalizada = feedUrl == null ? null : feedUrl.trim();
        return sources.findByFeedUrl(normalizada)
                .orElseGet(() -> sources.save(Source.register(name, normalizada)));
    }
}
