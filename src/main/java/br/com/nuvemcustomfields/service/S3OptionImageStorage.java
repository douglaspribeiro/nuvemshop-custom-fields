package br.com.nuvemcustomfields.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.time.Duration;

@Component
public class S3OptionImageStorage implements OptionImageStorage {
    private final String bucket;
    private final String prefix;
    private final S3Client client;
    private final S3Presigner presigner;
    @org.springframework.beans.factory.annotation.Autowired
    public S3OptionImageStorage(@Value("${images.s3.bucket:}") String bucket,
            @Value("${images.s3.region:us-east-2}") String region,
            @Value("${images.s3.prefix:production/options}") String prefix) {
        this.bucket = bucket.strip(); this.prefix = prefix.replaceAll("^/+|/+$", "");
        if (this.prefix.isBlank()) throw new IllegalArgumentException("images.s3.prefix must not be empty");
        if (enabled()) {
            client = S3Client.builder().region(Region.of(region)).overrideConfiguration(c -> c
                .apiCallTimeout(Duration.ofSeconds(20)).apiCallAttemptTimeout(Duration.ofSeconds(8))).build();
            presigner = S3Presigner.builder().region(Region.of(region)).build();
        } else { client = null; presigner = null; }
    }
    S3OptionImageStorage(String bucket, String prefix, S3Client client, S3Presigner presigner) {
        this.bucket=bucket; this.prefix=prefix; this.client=client; this.presigner=presigner;
    }
    public boolean enabled() { return !bucket.isBlank(); }
    public String bucket() { return bucket; }
    public String prefix() { return prefix; }
    public void put(String bucket, String key, byte[] image) {
        client.putObject(r -> r.bucket(bucket).key(key).contentType("image/jpeg")
                .cacheControl("private, max-age=60"), RequestBody.fromBytes(image));
    }
    public String readUrl(String bucket, String key) {
        return presigner.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(o -> o.bucket(bucket).key(key))).url().toString();
    }
    public void delete(String bucket, String key) {
        // Delete all exact-key versions, including delete markers. Never delete a prefix neighbour.
        for (var page : client.listObjectVersionsPaginator(r -> r.bucket(bucket).prefix(key))) {
            for (var version : page.versions()) if (key.equals(version.key()))
                client.deleteObject(r -> r.bucket(bucket).key(key).versionId(version.versionId()));
            for (var marker : page.deleteMarkers()) if (key.equals(marker.key()))
                client.deleteObject(r -> r.bucket(bucket).key(key).versionId(marker.versionId()));
        }
        // For an unversioned bucket this also removes an object not returned in a version listing.
        // No extra delete marker is created when versioning is active.
        var versioning = client.getBucketVersioning(r -> r.bucket(bucket)).statusAsString();
        if (versioning == null || versioning.isBlank()) client.deleteObject(r -> r.bucket(bucket).key(key));
    }
    @PreDestroy public void close() { if (client != null) client.close(); if (presigner != null) presigner.close(); }
}
