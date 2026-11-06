package com.noticiasplaneta.ingest.adapter.out.lock;

import com.noticiasplaneta.ingest.application.port.out.ExecutionLock;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueo en memoria del proceso. Suficiente para una instancia; si algún día hay varias,
 * este adaptador se sustituye por uno sobre PostgreSQL (advisory lock) sin tocar el dominio.
 */
@Component
class InMemoryExecutionLock implements ExecutionLock {

    private final Set<String> held = ConcurrentHashMap.newKeySet();

    @Override
    public boolean tryAcquire(String key) {
        return held.add(key);
    }

    @Override
    public void release(String key) {
        held.remove(key);
    }
}
