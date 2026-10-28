package br.com.nuvemcustomfields.service;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import java.time.Duration;

/** Dedicated private bucket. Downloads are proxied with attachment headers. */
@Component
public class S3BuyerFileStorage implements BuyerFileStorage {
    private final String bucket;
    private final String prefix;
    private final S3Client s3;
    public S3BuyerFileStorage(@Value("${buyer-uploads.s3.bucket:}") String bucket,
            @Value("${buyer-uploads.s3.region:us-east-2}") String region,
            @Value("${buyer-uploads.s3.prefix:production/buyer-files}") String prefix) {
        this.bucket=bucket.strip(); this.prefix=prefix.replaceAll("^/+|/+$", "");
        if (this.prefix.isBlank()) throw new IllegalArgumentException("buyer upload prefix must not be empty");
        s3=enabled() ? S3Client.builder().region(Region.of(region)).overrideConfiguration(c -> c
                .apiCallTimeout(Duration.ofSeconds(60)).apiCallAttemptTimeout(Duration.ofSeconds(25))).build() : null;
    }
    public boolean enabled() { return !bucket.isBlank(); }
    public String bucket() { return bucket; }
    public String key(long storeId, String id) { return prefix+"/"+storeId+"/"+id; }
    public void put(String bucket, String key, byte[] bytes, String type) {
        // Refuse storage if the bucket could expose artwork publicly. Deployment must
        // enforce all four controls; bucket-owner-enforced disables object ACLs.
        var block=s3.getPublicAccessBlock(r -> r.bucket(bucket)).publicAccessBlockConfiguration();
        if (!Boolean.TRUE.equals(block.blockPublicAcls()) || !Boolean.TRUE.equals(block.ignorePublicAcls())
                || !Boolean.TRUE.equals(block.blockPublicPolicy()) || !Boolean.TRUE.equals(block.restrictPublicBuckets()))
            throw new IllegalStateException("buyer upload bucket is not private");
        s3.putObject(r -> r.bucket(bucket).key(key).contentType(type).cacheControl("no-store")
                .serverSideEncryption("AES256"), RequestBody.fromBytes(bytes));
    }
    public java.io.InputStream read(String bucket, String key) {
        return s3.getObject(r -> r.bucket(bucket).key(key));
    }
    public void delete(String bucket, String key) {
        for (var page:s3.listObjectVersionsPaginator(r -> r.bucket(bucket).prefix(key))) {
            for (var version:page.versions()) if (key.equals(version.key()))
                s3.deleteObject(r -> r.bucket(bucket).key(key).versionId(version.versionId()));
            for (var marker:page.deleteMarkers()) if (key.equals(marker.key()))
                s3.deleteObject(r -> r.bucket(bucket).key(key).versionId(marker.versionId()));
        }
        var versioning=s3.getBucketVersioning(r -> r.bucket(bucket)).statusAsString();
        if (versioning==null || versioning.isBlank()) s3.deleteObject(r -> r.bucket(bucket).key(key));
    }
    @PreDestroy public void close() { if(s3!=null) s3.close(); }
}
