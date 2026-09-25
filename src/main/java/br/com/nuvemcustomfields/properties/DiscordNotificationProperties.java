package br.com.nuvemcustomfields.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notifications.discord")
public record DiscordNotificationProperties(String paymentWebhookUrl) {
    public boolean configured() {
        return paymentWebhookUrl != null && !paymentWebhookUrl.isBlank();
    }
}
