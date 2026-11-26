package com.noticiasplaneta.ingest.application.service;

import com.noticiasplaneta.ingest.application.port.in.SourceTrustUseCase;
import com.noticiasplaneta.ingest.application.port.out.SourceTrustRepository;
import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * F6 — marcar fuentes de confianza.
 *
 * <p>Cambiar quién puede publicar solo es una decisión editorial, así que queda en
 * {@code audit_log} con el antes y el después. Un identificador mal formado no rompe la
 * petición: devuelve {@code false} y el panel avisa.
 */
public class SourceTrustService implements SourceTrustUseCase {

    private final SourceTrustRepository sources;
    private final AuditPort audit;
    private final Clock clock;

    public SourceTrustService(SourceTrustRepository sources, AuditPort audit, Clock clock) {
        this.sources = sources;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public List<SourceTrustView> sources() {
        return sources.findAll().stream()
                .map(r -> new SourceTrustView(r.id(), r.name(), r.state(), r.trusted(), r.failureCount()))
                .toList();
    }

    @Override
    public boolean setTrusted(String sourceId, boolean trusted, String updatedBy) {
        UUID id;
        try {
            id = UUID.fromString(sourceId);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!sources.setTrusted(id, trusted)) {
            return false;
        }
        audit.record(new AuditEntry(
                updatedBy == null || updatedBy.isBlank() ? "system" : updatedBy,
                "HUMAN", "SOURCE_TRUST_CHANGED", "Source", id.toString(),
                String.valueOf(!trusted), String.valueOf(trusted), clock.instant()));
        return true;
    }
}
