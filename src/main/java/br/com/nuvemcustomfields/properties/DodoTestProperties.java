package br.com.nuvemcustomfields.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Arrays;
import br.com.nuvemcustomfields.payment.DodoConnection;
import br.com.nuvemcustomfields.entity.*;

/** Separate test provider: it can never select the live Dodo API. */
@ConfigurationProperties(prefix = "payments.dodo-test")
public record DodoTestProperties(boolean enabled, String apiKey, String webhookKey,
                                 String allowedStoreIds, int timeoutSeconds) implements DodoConnection {
    public PaymentProviderType provider() { return PaymentProviderType.DODO_TEST; }
    public PaymentEnvironment environment() { return PaymentEnvironment.SANDBOX; }
    public boolean allows(Long storeId) {
        if (storeId == null) return false;
        if (allowsAllStores()) return true;
        return Arrays.stream(allowedStoreIds.split(","))
                .map(String::strip).anyMatch(id -> id.equals(storeId.toString()));
    }
    public boolean allowsAllStores() {
        return allowedStoreIds == null || allowedStoreIds.isBlank()
                || Arrays.stream(allowedStoreIds.split(",")).map(String::strip).anyMatch("0"::equals);
    }
}
