package com.streamsense.analyticsservice.storage;

import java.util.Optional;

/**
 * Where a deal's logo bytes live. One bucket, keyed by the path the service chooses; the same
 * S3-compatible storage the captured frames are in, so ml-engine can read a logo the way it reads a
 * frame. A failure of the store is a {@link LogoStorageException}, never a silent no-op.
 */
public interface LogoObjectStore {

    String bucket();

    void put(String key, byte[] bytes, String contentType);

    /** The object's bytes, or empty when there is no such object. */
    Optional<byte[]> get(String key);

    /** Removes the object; removing one that is already gone is not an error. */
    void delete(String key);

    /** The URI the detection pipeline reads the object by. */
    default String ref(String key) {
        return "s3://" + bucket() + "/" + key;
    }
}
