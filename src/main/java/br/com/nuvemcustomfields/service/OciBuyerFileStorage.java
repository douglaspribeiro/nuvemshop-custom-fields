package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.BuyerStorageProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.Bucket;
import com.oracle.bmc.objectstorage.requests.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** Native OCI API: verifies bucket privacy and removes every version of an exact object. */
@Component
public class OciBuyerFileStorage implements BuyerFileStorage {
    private final String bucket;
    private final String namespace;
    private final String prefix;
    private final ObjectStorage client;

    @org.springframework.beans.factory.annotation.Autowired
    public OciBuyerFileStorage(@Value("${buyer-uploads.oci.bucket:}") String bucket,
            @Value("${buyer-uploads.oci.namespace:}") String namespace,
            @Value("${buyer-uploads.oci.region:sa-saopaulo-1}") String region,
            @Value("${buyer-uploads.oci.prefix:production/buyer-files}") String prefix,
            @Value("${buyer-uploads.oci.auth:CONFIG_FILE}") String auth,
            @Value("${buyer-uploads.oci.config-file:~/.oci/config}") String configFile,
            @Value("${buyer-uploads.oci.profile:DEFAULT}") String profile) {
        this.bucket = bucket.strip(); this.namespace = namespace.strip(); this.prefix = normalizePrefix(prefix);
        if (this.bucket.isBlank()) { client = null; return; }
        if (this.namespace.isBlank()) throw new IllegalArgumentException("buyer-uploads.oci.namespace is required");
        try {
            var builder = ObjectStorageClient.builder().region(region);
            client = switch (auth) {
                case "CONFIG_FILE" -> builder.build(new ConfigFileAuthenticationDetailsProvider(configFile, profile));
                case "INSTANCE_PRINCIPAL" -> builder.build(InstancePrincipalsAuthenticationDetailsProvider.builder().build());
                default -> throw new IllegalArgumentException("Invalid buyer-uploads.oci.auth");
            };
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load OCI buyer file storage authentication", e);
        }
    }
    OciBuyerFileStorage(String bucket, String namespace, String prefix, ObjectStorage client) {
        this.bucket = bucket; this.namespace = namespace; this.prefix = normalizePrefix(prefix); this.client = client;
    }
    private static String normalizePrefix(String prefix) {
        var normalized = prefix.replaceAll("^/+|/+$", "");
        if (normalized.isBlank()) throw new IllegalArgumentException("buyer upload prefix must not be empty");
        return normalized;
    }
    public BuyerStorageProvider provider() { return BuyerStorageProvider.OCI_OBJECT_STORAGE; }
    public boolean enabled() { return client != null; }
    public String bucket() { return bucket; }
    public String key(long storeId, String id) { return prefix + "/" + storeId + "/" + id; }
    private ObjectStorage client() {
        if (client == null) throw new IllegalStateException("OCI buyer file storage is not configured");
        return client;
    }
    public void put(String bucket, String key, byte[] bytes, String type) {
        var details = client().getBucket(GetBucketRequest.builder().namespaceName(namespace).bucketName(bucket).build()).getBucket();
        if (details.getPublicAccessType() != Bucket.PublicAccessType.NoPublicAccess)
            throw new IllegalStateException("buyer upload bucket is not private");
        client().putObject(PutObjectRequest.builder().namespaceName(namespace).bucketName(bucket).objectName(key)
                .contentType(type).cacheControl("no-store").contentLength((long) bytes.length)
                .putObjectBody(new ByteArrayInputStream(bytes)).build());
    }
    public InputStream read(String bucket, String key) {
        return client().getObject(GetObjectRequest.builder().namespaceName(namespace).bucketName(bucket).objectName(key).build()).getInputStream();
    }
    public void delete(String bucket, String key) {
        // Enumerate all versions first. Do not mutate a listing while following its page token.
        var versions = new java.util.ArrayList<String>();
        String start = null;
        do {
            var page = client().listObjectVersions(ListObjectVersionsRequest.builder().namespaceName(namespace)
                    .bucketName(bucket).prefix(key).page(start).build());
            for (var version : page.getObjectVersionCollection().getItems())
                if (key.equals(version.getName())) versions.add(version.getVersionId());
            start = page.getOpcNextPage();
        } while (start != null && !start.isBlank());
        for (var version : versions) client().deleteObject(DeleteObjectRequest.builder().namespaceName(namespace)
                .bucketName(bucket).objectName(key).versionId(version).build());
        // With versioning disabled, an absent object has no versions and DELETE is idempotent.
        // Never add a delete marker after removing a versioned object's versions.
        if (versions.isEmpty()) {
            var details = client().getBucket(GetBucketRequest.builder().namespaceName(namespace).bucketName(bucket).build()).getBucket();
            if (details.getVersioning() == Bucket.Versioning.Disabled) {
                try { client().deleteObject(DeleteObjectRequest.builder().namespaceName(namespace).bucketName(bucket).objectName(key).build()); }
                catch (com.oracle.bmc.model.BmcException e) { if (e.getStatusCode() != 404) throw e; }
            }
        }
    }
    @PreDestroy public void close() throws Exception { if (client != null) client.close(); }
}
