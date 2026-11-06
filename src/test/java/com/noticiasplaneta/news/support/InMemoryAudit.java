package com.noticiasplaneta.news.support;

import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;

import java.util.ArrayList;
import java.util.List;

public class InMemoryAudit implements AuditPort {

    private final List<AuditEntry> entries = new ArrayList<>();

    @Override
    public void record(AuditEntry entry) {
        entries.add(entry);
    }

    public List<AuditEntry> entries() {
        return List.copyOf(entries);
    }
}
