package br.com.nuvemcustomfields.properties;

import br.com.nuvemcustomfields.entity.PaymentEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Arrays;

@ConfigurationProperties(prefix = "payments.dlocal-go")
public record DlocalGoProperties(boolean enabled, boolean sandbox, String apiKey, String secretKey,
                                 String allowedStoreIds, int timeoutSeconds) {
    public boolean operational() { return apiKey != null && !apiKey.isBlank() && secretKey != null && !secretKey.isBlank(); }
    public boolean configured() { return enabled && operational(); }
    public PaymentEnvironment environment() { return sandbox ? PaymentEnvironment.SANDBOX : PaymentEnvironment.PRODUCTION; }
    public String baseUrl() { return sandbox ? "https://api-sbx.dlocalgo.com" : "https://api.dlocalgo.com"; }
    public String checkoutHost() { return sandbox ? "checkout-sbx.dlocalgo.com" : "checkout.dlocalgo.com"; }
    public boolean allows(Long id) {
        return !sandbox || (id != null && allowedStoreIds != null && Arrays.stream(allowedStoreIds.split(","))
                .map(String::trim).anyMatch(id.toString()::equals));
    }
    public int safeTimeoutSeconds() { return timeoutSeconds > 0 ? Math.min(timeoutSeconds, 60) : 20; }
}
