package br.com.nuvemcustomfields.properties;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.DodoConnection;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments.dodo")
public record DodoProperties(boolean enabled, String apiKey, String webhookKey, int timeoutSeconds) implements DodoConnection {
    public PaymentProviderType provider() { return PaymentProviderType.DODO; }
    public PaymentEnvironment environment() { return PaymentEnvironment.PRODUCTION; }
    public boolean allows(Long storeId) { return storeId != null; }
}
