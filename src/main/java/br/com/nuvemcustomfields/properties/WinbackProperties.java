package br.com.nuvemcustomfields.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "winback")
public record WinbackProperties(boolean enabled, String queueUrl, String region, String allowedStoreIds) {
    /** 0 (or blank) disables the allow-list and permits every store. */
    public boolean accepts(Long storeId) {
        if (storeId == null || allowedStoreIds == null || allowedStoreIds.isBlank()
                || allowedStoreIds.trim().equals("0")) return true;
        return java.util.Arrays.stream(allowedStoreIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .anyMatch(s -> s.equals(storeId.toString()));
    }
}
