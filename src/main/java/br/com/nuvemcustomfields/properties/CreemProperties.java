package br.com.nuvemcustomfields.properties;

import br.com.nuvemcustomfields.entity.PaymentEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments.creem")
public record CreemProperties(boolean enabled, boolean sandbox, String apiKey, String webhookSecret,
                              int graceDays, int timeoutSeconds) {
    public boolean apiConfigured() { return apiKey != null && !apiKey.isBlank(); }
    public boolean operational() { return apiConfigured() && webhookSecret != null && !webhookSecret.isBlank(); }
    public boolean configured() { return enabled && operational(); }
    public PaymentEnvironment environment() { return sandbox ? PaymentEnvironment.SANDBOX : PaymentEnvironment.PRODUCTION; }
    public String baseUrl() { return sandbox ? "https://test-api.creem.io/v1" : "https://api.creem.io/v1"; }
    public int safeTimeoutSeconds() { return timeoutSeconds > 0 ? Math.min(timeoutSeconds, 60) : 20; }
    public int safeGraceDays() { return Math.max(0, graceDays); }
}
