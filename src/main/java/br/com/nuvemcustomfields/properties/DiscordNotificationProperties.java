package br.com.nuvemcustomfields.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notifications.discord")
public record DiscordNotificationProperties(String paymentWebhookUrl, String supportWebhookUrl) {
    public boolean configured() {
        return paymentWebhookUrl != null && !paymentWebhookUrl.isBlank();
    }

    public boolean supportConfigured() {
        return supportWebhookUrl != null && !supportWebhookUrl.isBlank();
    }
}
