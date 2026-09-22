package com.streamsense.analyticsservice.storage;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The store for tests ({@code streamsense.logos.store=memory}): a map, lost with the process. */
public class InMemoryLogoObjectStore implements LogoObjectStore {

    private final String bucket;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    public InMemoryLogoObjectStore(String bucket) {
        this.bucket = bucket;
    }

    @Override
    public String bucket() {
        return bucket;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        objects.put(key, bytes.clone());
    }

    @Override
    public Optional<byte[]> get(String key) {
        return Optional.ofNullable(objects.get(key)).map(byte[]::clone);
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }

    /** Every key held, for tests that check what an upload or a deletion left behind. */
    public Set<String> keys() {
        return Set.copyOf(objects.keySet());
    }
}
