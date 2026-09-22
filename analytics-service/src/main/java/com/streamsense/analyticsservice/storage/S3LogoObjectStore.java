package com.streamsense.analyticsservice.storage;

import com.streamsense.analyticsservice.config.StreamSenseProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * The logo bucket on the S3-compatible store the frames live in (MinIO in Compose and Kubernetes),
 * reached with the frame storage credentials. The bucket is created on first use; every call is
 * bounded by the configured timeouts. Path-style addressing, because MinIO has no virtual hosts.
 */
public class S3LogoObjectStore implements LogoObjectStore, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(S3LogoObjectStore.class);

    private final S3Client client;
    private final String bucket;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public S3LogoObjectStore(StreamSenseProperties.Logos config) {
        this(config, buildClient(config));
    }

    S3LogoObjectStore(StreamSenseProperties.Logos config, S3Client client) {
        this.bucket = required(config.getBucket(), "streamsense.logos.bucket");
        this.client = client;
    }

    private static S3Client buildClient(StreamSenseProperties.Logos config) {
        String endpoint = required(config.getEndpoint(), "streamsense.logos.endpoint");
        String accessKey = required(config.getAccessKey(), "streamsense.logos.access-key");
        String secretKey = required(config.getSecretKey(), "streamsense.logos.secret-key");
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                        .socketTimeout(Duration.ofMillis(config.getReadTimeoutMs())))
                .build();
    }

    private static String required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is required when streamsense.logos.store is s3");
        }
        return value.trim();
    }

    @Override
    public String bucket() {
        return bucket;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        try {
            ensureBucket();
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (SdkException ex) {
            throw new LogoStorageException("could not store logo " + ref(key), ex);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(client.getObjectAsBytes(
                            GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .asByteArray());
        } catch (NoSuchKeyException | NoSuchBucketException ex) {
            return Optional.empty();
        } catch (SdkException ex) {
            throw new LogoStorageException("could not read logo " + ref(key), ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchBucketException ex) {
            // Nothing to delete from.
        } catch (SdkException ex) {
            throw new LogoStorageException("could not delete logo " + ref(key), ex);
        }
    }

    /** Creates the bucket the first time it is needed; a bucket someone else created meanwhile is fine. */
    private void ensureBucket() {
        if (bucketReady.get()) {
            return;
        }
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException missing) {
            try {
                client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("created logo bucket {}", bucket);
            } catch (BucketAlreadyOwnedByYouException raced) {
                // Created between the head and the create.
            }
        }
        bucketReady.set(true);
    }

    @Override
    public void close() {
        client.close();
    }
}
