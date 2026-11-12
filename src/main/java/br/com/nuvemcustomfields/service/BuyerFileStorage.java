package br.com.nuvemcustomfields.service;

public interface BuyerFileStorage {
    default br.com.nuvemcustomfields.entity.BuyerStorageProvider provider() {
        return br.com.nuvemcustomfields.entity.BuyerStorageProvider.AWS_S3;
    }
    default BuyerFileStorage forProvider(br.com.nuvemcustomfields.entity.BuyerStorageProvider provider) {
        if (provider != provider()) throw new IllegalStateException("Buyer file storage provider unavailable: " + provider);
        return this;
    }
    default BuyerFileStorage forStore(long storeId) { return this; }
    boolean enabled();
    String bucket();
    String key(long storeId, String id);
    void put(String bucket, String key, byte[] bytes, String contentType);
    java.io.InputStream read(String bucket, String key);
    void delete(String bucket, String key);
}
