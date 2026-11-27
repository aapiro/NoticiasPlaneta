package com.noticiasplaneta.ingest.application.service;

import com.noticiasplaneta.ingest.application.port.in.FuenteAdminUseCase;
import com.noticiasplaneta.ingest.application.port.out.SourceRepository;
import com.noticiasplaneta.ingest.application.port.out.SourceTrustRepository;
import com.noticiasplaneta.ingest.domain.Source;
import com.noticiasplaneta.ingest.domain.SourceId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class FuenteAdminService implements FuenteAdminUseCase {

    private final SourceRepository sources;
    private final SourceTrustRepository trust;

    FuenteAdminService(SourceRepository sources, SourceTrustRepository trust) {
        this.sources = sources;
        this.trust = trust;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Fuente> fuentes() {
        return trust.findAll().stream()
                .map(r -> new Fuente(r.id(), r.name(), r.feedUrl(), r.state(),
                        r.trusted(), r.failureCount(), r.lastCheckedAt()))
                .toList();
    }

    @Override
    @Transactional
    public String crear(String nombre, String feedUrl) {
        // Las reglas (nombre obligatorio, http/https) las pone el dominio.
        Source source = Source.register(nombre, feedUrl);
        return sources.save(source).id().value().toString();
    }

    @Override
    @Transactional
    public boolean activar(String id) {
        return cambiar(id, Source::enable);
    }

    @Override
    @Transactional
    public boolean desactivar(String id) {
        return cambiar(id, Source::disable);
    }

    private boolean cambiar(String id, java.util.function.Consumer<Source> cambio) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return sources.findById(new SourceId(uuid))
                .map(source -> {
                    cambio.accept(source);
                    sources.save(source);
                    return true;
                })
                .orElse(false);
    }
}
