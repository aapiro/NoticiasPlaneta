package com.noticiasplaneta.media.support;

import com.noticiasplaneta.media.application.port.out.ObjectStoragePort;
import com.noticiasplaneta.shared.domain.DomainException;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FakeStorage implements ObjectStoragePort {

    private final Map<String, byte[]> objects = new HashMap<>();
    private final Set<String> deleted = new HashSet<>();

    @Override
    public String presignPut(String key, String contentType, Duration ttl) {
        return "https://fake/" + key;
    }

    @Override
    public void put(String key, byte[] content) {
        objects.put(key, content);
    }

    @Override
    public byte[] get(String key) {
        byte[] data = objects.get(key);
        if (data == null) {
            throw new IllegalStateException("no existe " + key);
        }
        return data;
    }

    @Override
    public void putSigned(String key, long expiresAt, String token, byte[] content) {
        objects.put(key, content);
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
        deleted.add(key);
    }

    public void seed(String key, byte[] content) {
        objects.put(key, content);
    }

    public boolean wasDeleted(String key) {
        return deleted.contains(key);
    }

    public Map<String, byte[]> objects() {
        return Map.copyOf(objects);
    }
}
