package br.com.nuvemcustomfields.service;

public interface OptionImageStorage {
    boolean enabled();
    String bucket();
    String prefix();
    void put(String bucket, String key, byte[] image);
    String readUrl(String bucket, String key);
    void delete(String bucket, String key);
}
