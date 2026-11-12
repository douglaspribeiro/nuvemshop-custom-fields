package br.com.nuvemcustomfields.properties;

import br.com.nuvemcustomfields.entity.PaymentEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments.stripe")
public record StripeProperties(boolean enabled, boolean sandbox, String secretKey, String webhookSecret,
                               int graceDays, int timeoutSeconds) {
    public boolean apiConfigured() {
        String mode = sandbox ? "test_" : "live_";
        return secretKey != null && secretKey.length() > 12
                && (secretKey.startsWith("sk_" + mode) || secretKey.startsWith("rk_" + mode));
    }
    public boolean operational() {
        return apiConfigured() && webhookSecret != null && webhookSecret.startsWith("whsec_")
                && webhookSecret.length() > 8;
    }
    public boolean configured() { return enabled && operational(); }
    public PaymentEnvironment environment() { return sandbox ? PaymentEnvironment.SANDBOX : PaymentEnvironment.PRODUCTION; }
    public int safeTimeoutSeconds() { return timeoutSeconds > 0 ? Math.min(timeoutSeconds, 60) : 20; }
    public int safeGraceDays() { return Math.max(0, graceDays); }
}
