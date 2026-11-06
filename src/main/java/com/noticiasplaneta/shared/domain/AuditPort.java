package com.noticiasplaneta.shared.domain;

public interface AuditPort {
    void record(AuditEntry entry);
}
