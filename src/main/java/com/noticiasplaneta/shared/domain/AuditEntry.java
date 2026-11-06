package com.noticiasplaneta.shared.domain;

import java.time.Instant;

/** Registro de auditoría: quién hizo qué, sobre qué, y con qué antes/después. */
public record AuditEntry(
        String actorId,
        String actorType,
        String action,
        String entity,
        String entityId,
        String before,
        String after,
        Instant at) {
}
