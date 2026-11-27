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
    private final com.noticiasplaneta.ingest.application.port.out.AvisoRepository avisos;

    FuenteAdminService(SourceRepository sources, SourceTrustRepository trust,
                       com.noticiasplaneta.ingest.application.port.out.AvisoRepository avisos) {
        this.sources = sources;
        this.trust = trust;
        this.avisos = avisos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Aviso> historial(int limite) {
        return avisos.recientes(Math.clamp(limite, 1, 200));
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
    public boolean editar(String id, String nombre, String feedUrl) {
        return cambiar(id, source -> source.editar(nombre, feedUrl));
    }

    @Override
    @Transactional
    public Borrado eliminar(String id) {
        UUID uuid = uuid(id);
        if (uuid == null) return Borrado.NO_EXISTE;

        SourceId sourceId = new SourceId(uuid);
        var source = sources.findById(sourceId);
        if (source.isEmpty()) return Borrado.NO_EXISTE;

        // Con piezas dentro no se borra: `ingest_item` tiene FK a `source`, y las
        // noticias ya ingeridas perderían su origen. Se desactiva, que deja de leerse
        // igual y conserva el rastro.
        if (sources.countItems(sourceId) > 0) {
            Source existente = source.get();
            existente.disable();
            sources.save(existente);
            return Borrado.DESACTIVADA_TENIA_PIEZAS;
        }

        sources.delete(sourceId);
        return Borrado.BORRADA;
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

    private static UUID uuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private boolean cambiar(String id, java.util.function.Consumer<Source> cambio) {
        UUID uuid = uuid(id);
        if (uuid == null) return false;
        return sources.findById(new SourceId(uuid))
                .map(source -> {
                    cambio.accept(source);
                    sources.save(source);
                    return true;
                })
                .orElse(false);
    }
}
