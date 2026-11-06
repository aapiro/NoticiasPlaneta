package com.noticiasplaneta.ingest.support;

import com.noticiasplaneta.ingest.application.port.out.ExecutionLock;

import java.util.HashSet;
import java.util.Set;

public class InMemoryLock implements ExecutionLock {

    private final Set<String> held = new HashSet<>();

    @Override
    public boolean tryAcquire(String key) {
        return held.add(key);
    }

    @Override
    public void release(String key) {
        held.remove(key);
    }
}
