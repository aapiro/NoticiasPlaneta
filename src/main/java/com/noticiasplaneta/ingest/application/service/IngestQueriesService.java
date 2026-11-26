package com.noticiasplaneta.ingest.application.service;

import com.noticiasplaneta.ingest.application.port.in.GetIngestItemUseCase;
import com.noticiasplaneta.ingest.application.port.in.IngestAllSourcesUseCase;
import com.noticiasplaneta.ingest.application.port.in.IngestItemView;
import com.noticiasplaneta.ingest.application.port.in.IngestSourceUseCase;
import com.noticiasplaneta.ingest.application.port.out.SourceRepository;
import com.noticiasplaneta.ingest.domain.IngestItemId;
import com.noticiasplaneta.ingest.domain.Source;
import com.noticiasplaneta.ingest.domain.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

public class IngestQueriesService implements GetIngestItemUseCase, IngestAllSourcesUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngestQueriesService.class);

    private final SourceRepository sources;
    private final com.noticiasplaneta.ingest.application.port.out.IngestItemRepository items;
    private final IngestSourceUseCase ingestSource;

    public IngestQueriesService(SourceRepository sources,
                                com.noticiasplaneta.ingest.application.port.out.IngestItemRepository items,
                                IngestSourceUseCase ingestSource) {
        this.sources = sources;
        this.items = items;
        this.ingestSource = ingestSource;
    }

    @Override
    public Optional<IngestItemView> byId(UUID itemId) {
        return items.findById(new IngestItemId(itemId))
                .map(i -> new IngestItemView(i.id().value(), i.sourceId().value(), i.title(),
                        i.body(), i.canonicalUrl(), i.author()));
    }

    @Override
    public int runAll() {
        int done = 0;
        for (Source source : sources.findByState(SourceState.ACTIVE)) {
            try {
                var report = ingestSource.run(source.id());
                if (report.created() > 0) {
                    done += report.created();
                }
            } catch (RuntimeException e) {
                log.warn("Ingesta fallida para {}: {}", source.name(), e.getMessage());
            }
        }
        return done;
    }

    @Override
    public InformeFuentes runDetailed() {
        // DEGRADED es un aviso, no una baja: se sigue intentando. DISABLED no se toca.
        java.util.List<Source> fuentes = new java.util.ArrayList<>(sources.findByState(SourceState.ACTIVE));
        fuentes.addAll(sources.findByState(SourceState.DEGRADED));

        java.util.List<FuenteIngesta> informe = new java.util.ArrayList<>();
        int creadas = 0;
        int errores = 0;

        for (Source source : fuentes) {
            String id = source.id().value().toString();
            try {
                var report = ingestSource.run(source.id());
                creadas += report.created();
                if (report.error() == null) {
                    informe.add(new FuenteIngesta(id, source.name(), report.created(), null));
                } else {
                    errores++;
                    informe.add(new FuenteIngesta(id, source.name(), 0, report.error()));
                }
            } catch (RuntimeException e) {
                errores++;
                log.warn("Ingesta fallida para {}: {}", source.name(), e.getMessage());
                informe.add(new FuenteIngesta(id, source.name(), 0,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        return new InformeFuentes(informe, creadas, errores);
    }
}
