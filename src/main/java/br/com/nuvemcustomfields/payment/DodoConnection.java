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
        String prefix = environment() == PaymentEnvironment.SANDBOX ? "dodo_test_" : "dodo_live_";
        return apiKey() != null && apiKey().startsWith(prefix) && apiKey().length() > prefix.length();
    }
    default boolean operational() { return apiConfigured() && webhookKey() != null && !webhookKey().isBlank(); }
    default boolean configured() { return enabled() && operational(); }
    default int safeTimeoutSeconds() { return timeoutSeconds() > 0 ? Math.min(timeoutSeconds(), 60) : 20; }
}
