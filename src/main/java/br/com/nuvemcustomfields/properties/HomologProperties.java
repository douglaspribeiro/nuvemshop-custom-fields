package br.com.nuvemcustomfields.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Arrays;

/** Shared access policy for features under homologation, never an application-wide store block. */
@ConfigurationProperties(prefix = "homolog")
public record HomologProperties(String allowedStoreIds) {
    public boolean allowsAllStores() { return "*".equals(normalized()); }
    public boolean allows(Long storeId) {
        if (storeId == null || storeId <= 0) return false;
        if (allowsAllStores()) return true;
        String value = normalized();
        if (value.isEmpty() || value.equals("0")) return false;
        String[] ids = value.split(",", -1);
        // Malformed configuration fails closed, including mixed wildcard/zero lists.
        if (Arrays.stream(ids).anyMatch(id -> !id.strip().matches("[1-9][0-9]*"))) return false;
        return Arrays.stream(ids).map(String::strip).anyMatch(storeId.toString()::equals);
    }
    private String normalized() { return allowedStoreIds == null ? "" : allowedStoreIds.strip(); }
}
