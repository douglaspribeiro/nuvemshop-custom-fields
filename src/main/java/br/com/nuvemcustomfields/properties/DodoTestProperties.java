package br.com.nuvemcustomfields.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import br.com.nuvemcustomfields.payment.DodoConnection;
import br.com.nuvemcustomfields.entity.*;

/** Separate test provider: it can never select the live Dodo API. */
@ConfigurationProperties(prefix = "payments.dodo-test")
public record DodoTestProperties(boolean enabled, String apiKey, String webhookKey,
                                 int timeoutSeconds) implements DodoConnection {
    public PaymentProviderType provider() { return PaymentProviderType.DODO_TEST; }
    public PaymentEnvironment environment() { return PaymentEnvironment.SANDBOX; }
    public boolean allows(Long storeId) { return storeId != null; }
}
