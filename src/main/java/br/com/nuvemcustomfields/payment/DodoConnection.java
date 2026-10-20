package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.*;

public interface DodoConnection {
    boolean enabled();
    String apiKey();
    String webhookKey();
    int timeoutSeconds();
    PaymentProviderType provider();
    PaymentEnvironment environment();
    boolean allows(Long storeId);
    default boolean apiConfigured() {
        // Dodo may issue opaque API-key formats. The gateway's fixed base URL is
        // the authority for the environment, and the API authenticates the key.
        return apiKey() != null && !apiKey().isBlank();
    }
    default boolean operational() { return apiConfigured() && webhookKey() != null && !webhookKey().isBlank(); }
    default boolean configured() { return enabled() && operational(); }
    default int safeTimeoutSeconds() { return timeoutSeconds() > 0 ? Math.min(timeoutSeconds(), 60) : 20; }
}
