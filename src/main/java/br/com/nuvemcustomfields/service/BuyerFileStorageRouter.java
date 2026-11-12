package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.BuyerStorageProvider;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** The default and store override affect writes only. Existing files carry their destination. */
@Component
@Primary
public class BuyerFileStorageRouter implements BuyerFileStorage {
    private final BuyerStorageProvider defaultProvider;
    private final S3BuyerFileStorage aws;
    private final OciBuyerFileStorage oci;
    private final StoreRepository stores;

    public BuyerFileStorageRouter(@Value("${buyer-uploads.provider:AWS_S3}") BuyerStorageProvider defaultProvider,
            S3BuyerFileStorage aws, OciBuyerFileStorage oci, StoreRepository stores) {
        this.defaultProvider = defaultProvider; this.aws = aws; this.oci = oci; this.stores = stores;
    }
    public BuyerStorageProvider provider() { return defaultProvider; }
    public BuyerFileStorage forProvider(BuyerStorageProvider provider) {
        if (provider == null) throw new IllegalStateException("Buyer file has no storage provider");
        return switch (provider) { case AWS_S3 -> aws; case OCI_OBJECT_STORAGE -> oci; };
    }
    public BuyerFileStorage forStore(long storeId) {
        var store = stores.findByStoreId(storeId).orElseThrow();
        var override = store.getBuyerUploadStorageProvider();
        return forProvider(override == null ? defaultProvider : override);
    }
    public boolean enabled() { return forProvider(defaultProvider).enabled(); }
    public String bucket() { return forProvider(defaultProvider).bucket(); }
    public String key(long storeId, String id) { return forStore(storeId).key(storeId, id); }
    public void put(String bucket, String key, byte[] bytes, String type) { forProvider(defaultProvider).put(bucket, key, bytes, type); }
    public java.io.InputStream read(String bucket, String key) { return forProvider(defaultProvider).read(bucket, key); }
    public void delete(String bucket, String key) { forProvider(defaultProvider).delete(bucket, key); }
}
