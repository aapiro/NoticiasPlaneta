package com.noticiasplaneta.news.adapter.out.persistence;

import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auditoría append-only. En su propia transacción (REQUIRES_NEW) para que un fallo posterior
 * del caso de uso no borre el rastro del intento.
 */
@Component
class AuditLogAdapter implements AuditPort {

    private final SpringDataAuditLogRepository jpa;

    AuditLogAdapter(SpringDataAuditLogRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEntry entry) {
        AuditLogJpaEntity e = new AuditLogJpaEntity();
        e.actorId = entry.actorId();
        e.actorType = entry.actorType();
        e.action = entry.action();
        e.entity = entry.entity();
        e.entityId = entry.entityId();
        e.before = entry.before();
        e.after = entry.after();
        e.at = entry.at();
        jpa.save(e);
    }
}
