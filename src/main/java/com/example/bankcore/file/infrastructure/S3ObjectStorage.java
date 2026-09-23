package com.example.bankcore.file.infrastructure;

import com.example.bankcore.file.config.FileProperties;
import com.example.bankcore.file.domain.ObjectStorage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.URI;

/**
 * Object storage over the S3 protocol, which is what MinIO speaks.
 *
 * <p>Path-style access is forced on: the virtual-host style S3 prefers turns a bucket into a
 * subdomain, which a local MinIO on {@code localhost:9000} cannot serve.
 */
@Component
public class S3ObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final FileProperties properties;
    private final S3Client client;

    public S3ObjectStorage(FileProperties properties) {
        this.properties = properties;
        this.client = S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    /**
     * Creates the bucket if it is missing.
     *
     * <p>Every failure here is logged rather than thrown, including "the endpoint is not
     * reachable at all". Object storage is one feature among many: an application that refuses
     * to start because MinIO is down would take authentication, accounts and transfers offline
     * with it. The file endpoints fail on their own when they are used, which is where the
     * failure belongs.
     */
    @PostConstruct
    void ensureBucketExists() {
        try {
            client.headBucket(builder -> builder.bucket(properties.bucket()));
            return;
        } catch (RuntimeException missingOrUnreachable) {
            // Fall through and try to create it.
        }

        try {
            client.createBucket(builder -> builder.bucket(properties.bucket()));
            log.info("Created object storage bucket: {}", properties.bucket());
        } catch (RuntimeException ex) {
            log.warn("Object storage is not reachable at {}; file endpoints will fail until it is: {}",
                    properties.endpoint(), ex.getMessage());
        }
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        client.putObject(PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public InputStream get(String key) {
        return client.getObject(GetObjectRequest.builder()
                .bucket(properties.bucket()).key(key).build());
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket()).key(key).build());
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket()).key(key).build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        }
    }
}
