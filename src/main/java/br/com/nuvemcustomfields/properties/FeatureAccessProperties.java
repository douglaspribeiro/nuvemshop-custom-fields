package br.com.nuvemcustomfields.properties;

import java.util.Arrays;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Store-specific feature grants. Configuration errors deny every grant rather than widening access. */
@ConfigurationProperties(prefix = "features")
public record FeatureAccessProperties(String allowed) {
    public enum Feature {
        APPS_MENU("apps-menu"),
        PRODUCT_COMPLEMENTS("product-complements"),
        SCHEDULED_PIX("scheduled-pix");

        private final String key;
        Feature(String key) { this.key = key; }
        public String key() { return key; }
    }

    public boolean allows(Long storeId, Feature feature) {
        if (storeId == null || storeId <= 0 || feature == null) return false;
        String value = normalized();
        if (value.isEmpty()) return false;
        String[] grants = value.split(",", -1);
        if (Arrays.stream(grants).anyMatch(grant -> !grant.strip().matches("[1-9][0-9]*:[a-z][a-z0-9_-]*"))) return false;
        return Arrays.stream(grants).map(String::strip).anyMatch((storeId + ":" + feature.key())::equals);
    }

    private String normalized() {
        return allowed == null ? "" : allowed.strip();
    }
}
