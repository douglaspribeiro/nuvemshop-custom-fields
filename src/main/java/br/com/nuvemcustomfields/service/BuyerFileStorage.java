package br.com.nuvemcustomfields.service;

public interface BuyerFileStorage {
    boolean enabled();
    String bucket();
    String key(long storeId, String id);
    void put(String bucket, String key, byte[] bytes, String contentType);
    java.io.InputStream read(String bucket, String key);
    void delete(String bucket, String key);
}
