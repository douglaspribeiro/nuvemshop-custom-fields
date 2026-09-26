package br.com.nuvemcustomfields.properties;

import br.com.nuvemcustomfields.entity.PaymentEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments.paddle")
public record PaddleProperties(
        boolean enabled,
        boolean sandbox,
        String apiBaseUrl,
        String apiKey,
        String clientSideToken,
        String webhookSecret,
        String apiVersion,
        int graceDays,
        long webhookToleranceSeconds,
        long checkoutTokenMinutes
) {
    public boolean configured() {
        return enabled && text(apiBaseUrl) && text(apiKey) && text(clientSideToken) && text(webhookSecret);
    }
    public PaymentEnvironment environment() { return sandbox ? PaymentEnvironment.SANDBOX : PaymentEnvironment.PRODUCTION; }
    public int safeGraceDays() { return Math.max(0, graceDays); }
    public long safeWebhookToleranceSeconds() { return webhookToleranceSeconds > 0 ? webhookToleranceSeconds : 300; }
    public long safeCheckoutTokenMinutes() { return checkoutTokenMinutes > 0 ? checkoutTokenMinutes : 30; }
    public String safeApiVersion() { return text(apiVersion) ? apiVersion : "1"; }
    private static boolean text(String value) { return value != null && !value.isBlank(); }
}
