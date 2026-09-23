package com.example.bankcore.file.domain;

import java.io.InputStream;

/**
 * Where the bytes actually go.
 *
 * <p>A port, so the domain talks about storing and fetching objects rather than about S3. MinIO
 * stands in for object storage locally and the same adapter works against a real bucket in
 * production — which is the only reason MinIO is worth running in development at all.
 */
public interface ObjectStorage {

    /** Stores the content under {@code key}. Overwrites, so keys must be unique by construction. */
    void put(String key, byte[] content, String contentType);

    InputStream get(String key);

    void delete(String key);

    boolean exists(String key);
}
